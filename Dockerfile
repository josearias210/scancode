# Android build environment with Gradle 9.9.2 and JDK 17.
FROM gradle:9.9.2-jdk17

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=$PATH:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools

RUN apt-get update && apt-get install -y --no-install-recommends unzip wget \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p $ANDROID_HOME/cmdline-tools \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip -O /tmp/tools.zip \
    && echo '4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583  /tmp/tools.zip' | sha256sum --check \
    && unzip -q /tmp/tools.zip -d $ANDROID_HOME/cmdline-tools \
    && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
    && rm /tmp/tools.zip

RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager "platforms;android-37.0" "build-tools;37.0.0" "platform-tools"

ARG INCLUDE_ANDROID_EMULATOR=false
RUN if [ "$INCLUDE_ANDROID_EMULATOR" = true ]; then \
      sdkmanager --install emulator 'system-images;android-30;aosp_atd;x86_64'; \
    fi
RUN if [ "$INCLUDE_ANDROID_EMULATOR" = true ]; then \
      apt-get update && apt-get install -y --no-install-recommends libx11-6 libx11-xcb1 \
      && rm -rf /var/lib/apt/lists/*; \
    fi

WORKDIR /project
