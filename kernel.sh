#! /bin/bash

database=/home/hzy/hzy/projects/android/auiauto/database
mkdir -p ${database}/kernel/
cp app/build/outputs/apk/debug/app-debug.apk ${database}/kernel/antrance.apk
cp -r app/build/intermediates/javac/debug/classes/*.class ${database}/kernel/