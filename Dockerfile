FROM eclipse-temurin:17-jdk

WORKDIR /app

# Install required tools
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl unzip && \
    rm -rf /var/lib/apt/lists/*

# Install Android command-line tools
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    curl -sL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-tmp && \
    mv /tmp/cmdline-tools-tmp/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-tools-tmp

# Accept licenses and install SDK components
RUN yes | sdkmanager --licenses > /dev/null 2>&1 || true && \
    sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"

# Install Gradle and generate wrapper
RUN curl -sL https://services.gradle.org/distributions/gradle-8.11.1-bin.zip -o /tmp/gradle.zip && \
    unzip -q /tmp/gradle.zip -d /opt && \
    rm /tmp/gradle.zip
ENV PATH="/opt/gradle-8.11.1/bin:${PATH}"

# Copy project files
COPY . .

# Generate wrapper
RUN gradle wrapper --gradle-version 8.11.1

# Build
CMD ["./gradlew", "assembleDebug", "--no-daemon", "--stacktrace"]
