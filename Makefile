.PHONY: build clean docker-image

DOCKER_IMAGE := sasayaki-build
APK_OUTPUT := sasayaki-debug.apk
GRADLE_USER_HOME := $(CURDIR)/.gradle-cache
ANDROID_SDK_HOME := $(CURDIR)/.android-cache
HOST_UID := $(shell id -u)
HOST_GID := $(shell id -g)

docker-image:
	docker build -t $(DOCKER_IMAGE) .

build: docker-image
	mkdir -p $(GRADLE_USER_HOME) $(ANDROID_SDK_HOME)
	docker run --rm \
		-u 0:0 \
		-v $(CURDIR):/project \
		-v $(GRADLE_USER_HOME):/home/gradle/.gradle \
		-v $(ANDROID_SDK_HOME):/home/gradle/.android \
		-e GRADLE_USER_HOME=/home/gradle/.gradle \
		-w /project $(DOCKER_IMAGE) \
		sh -c "./gradlew assembleDebug && chown -R $(HOST_UID):$(HOST_GID) /project/app/build /project/.gradle-cache /project/.android-cache"
	cp app/build/outputs/apk/debug/app-debug.apk $(APK_OUTPUT)
	@echo "APK built: $(APK_OUTPUT)"
	@ls -lh $(APK_OUTPUT)

clean:
	rm -rf app/build build .gradle .gradle-cache .android-cache $(APK_OUTPUT)
