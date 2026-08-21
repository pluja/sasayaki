.PHONY: build test debug clean docker-image

DOCKER_IMAGE := sasayaki-build
APK_OUTPUT := sasayaki-release.apk
GRADLE_USER_HOME := $(CURDIR)/.gradle-cache
GRADLE_BUILD_HOME := $(CURDIR)/.gradle-build-cache
ANDROID_SDK_HOME := $(CURDIR)/.android-cache
HOST_UID := $(shell id -u)
HOST_GID := $(shell id -g)

# Gradle runs as root so it can write the mounted caches, then hands everything it
# touched back to the invoking user; without the chown the next host-side build fails
# on root-owned files under app/build.
define gradle
	mkdir -p $(GRADLE_BUILD_HOME) $(ANDROID_SDK_HOME)
	docker run --rm \
		-u 0:0 \
		-v $(CURDIR):/project \
		-v $(GRADLE_BUILD_HOME):/home/gradle/.gradle \
		-v $(ANDROID_SDK_HOME):/home/gradle/.android \
		-e GRADLE_USER_HOME=/home/gradle/.gradle \
		-w /project $(DOCKER_IMAGE) \
		sh -c "./gradlew --no-daemon $(1) && chown -Rf $(HOST_UID):$(HOST_GID) /project/app/build /project/app/schemas /project/.gradle-build-cache /project/.android-cache"
endef

docker-image:
	docker build -t $(DOCKER_IMAGE) .

build: docker-image
	$(call gradle,assembleRelease)
	cp app/build/outputs/apk/release/app-release.apk $(APK_OUTPUT)
	@echo "APK built: $(APK_OUTPUT)"
	@ls -lh $(APK_OUTPUT)

test: docker-image
	$(call gradle,test)

debug: docker-image
	$(call gradle,assembleDebug)

clean:
	rm -rf app/build build .gradle .gradle-cache .gradle-build-cache .android-cache $(APK_OUTPUT)
