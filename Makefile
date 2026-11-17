.PHONY: build release-key test unit debug verify check format hooks screenshots clean docker-image

DOCKER_IMAGE := sasayaki-build
APK_OUTPUT := sasayaki-release.apk
UNSIGNED_APK := app/build/outputs/apk/release/app-release-unsigned.apk
GRADLE_USER_HOME := $(CURDIR)/.gradle-cache
GRADLE_BUILD_HOME := $(CURDIR)/.gradle-build-cache
ANDROID_SDK_HOME := $(CURDIR)/.android-cache
HOST_UID := $(shell id -u)
HOST_GID := $(shell id -g)
# Rootless podman already maps the container's root to the invoking user, and a chown to
# that user's uid would map it onto a subordinate uid the host user cannot write.
HAND_BACK := $(if $(findstring podman,$(shell docker --version 2>/dev/null)),true,chown -Rf $(HOST_UID):$(HOST_GID))
# SELinux hosts (Fedora) deny containers access to the mounted project unless labeling is off.
DOCKER_RUN := docker run --rm --security-opt label=disable

# Gradle runs as root so it can write the mounted caches, then hands everything it
# touched back to the invoking user; without that the next host-side build fails on
# root-owned files under app/build.
define gradle
	mkdir -p $(GRADLE_BUILD_HOME) $(ANDROID_SDK_HOME)
	$(DOCKER_RUN) \
		-u 0:0 \
		-v $(CURDIR):/project \
		-v $(GRADLE_BUILD_HOME):/home/gradle/.gradle \
		-v $(ANDROID_SDK_HOME):/home/gradle/.android \
		-e GRADLE_USER_HOME=/home/gradle/.gradle \
		-w /project $(DOCKER_IMAGE) \
		sh -c "./gradlew --no-daemon $(1); status=\$$?; $(HAND_BACK) /project/app /project/build /project/.gradle-build-cache /project/.android-cache 2>/dev/null; exit \$$status"
endef

docker-image:
	docker build -t $(DOCKER_IMAGE) .

# Builds and signs the release APK. Reads the keystore password from
# SASAYAKI_KEYSTORE_PASSWORD, or asks for it.
build: docker-image
	@test -f signing/release.jks || { echo "signing/release.jks is missing; see Release signing in README.md"; exit 1; }
	$(call gradle,assembleRelease)
	@if [ -z "$$SASAYAKI_KEYSTORE_PASSWORD" ]; then \
		trap 'stty echo' EXIT INT; stty -echo; printf 'Release keystore password: '; read -r SASAYAKI_KEYSTORE_PASSWORD; stty echo; echo; \
	fi; \
	export SASAYAKI_KEYSTORE_PASSWORD; \
	$(DOCKER_RUN) -u 0:0 -e SASAYAKI_KEYSTORE_PASSWORD -v $(CURDIR):/project -w /project $(DOCKER_IMAGE) \
		sh -c "scripts/sign-release.sh $(UNSIGNED_APK) $(APK_OUTPUT) && $(HAND_BACK) $(APK_OUTPUT)"
	@echo "APK built: $(APK_OUTPUT)"
	@ls -lh $(APK_OUTPUT)

# Creates the release key and its rotation proof from the old debug key. Run once.
release-key: docker-image
	$(DOCKER_RUN) -it -u 0:0 -v $(CURDIR):/project -w /project $(DOCKER_IMAGE) \
		sh -c "scripts/create-release-key.sh && $(HAND_BACK) signing"

test: docker-image
	$(call gradle,test)

# Runs the debug unit tests matching TESTS, e.g. make unit TESTS='*SpeechDetector*'.
unit: docker-image
	$(call gradle,testDebugUnitTest --tests '$(TESTS)')

debug: docker-image
	$(call gradle,assembleDebug)

verify: docker-image
	$(call gradle,test assembleDebug)

# The pre-push gate (lefthook.yml): formatting, lint and unit tests.
check: docker-image
	$(call gradle,spotlessCheck lintDebug testDebugUnitTest)

format: docker-image
	$(call gradle,spotlessApply)

hooks:
	lefthook install

# Renders the screenshot tests to app/build/screenshots for visual review.
screenshots: docker-image
	$(call gradle,testDebugUnitTest -Pscreenshots)

clean:
	rm -rf app/build build .gradle .gradle-cache .gradle-build-cache .android-cache $(APK_OUTPUT)
