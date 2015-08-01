#!/bin/bash

# Set up Xcode project (currently requires CocoaPods)
pod install

# Build ClojureScript (currently requires lein)
cd planck-cljs
script/build
script/bundle
cd ..

# Xcode
xcodebuild -workspace planck.xcworkspace -scheme planck -configuration Release
