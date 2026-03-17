.PHONY: build clean docker-image

DOCKER_IMAGE := sasayaki-build
APK_OUTPUT := sasayaki-debug.apk

docker-image:
	docker build -t $(DOCKER_IMAGE) .

build: docker-image
	docker run --rm -v $(CURDIR):/project -w /project $(DOCKER_IMAGE) \
		gradle --no-daemon assembleDebug
	cp app/build/outputs/apk/debug/app-debug.apk $(APK_OUTPUT)
	@echo "APK built: $(APK_OUTPUT)"
	@ls -lh $(APK_OUTPUT)

clean:
	rm -rf app/build build .gradle $(APK_OUTPUT)
