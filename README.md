# ConnectSDK-CompanionLibrary-Android

ConnectSDK-CompanionLibrary-Android forked from CastCompanionLibrary-android is a library project to enable developers integrate LG's ConnectSDK casting capabilities into their applications faster and easier.

## Fork
This project is basically a huge refactoring of the original CastCompanionLibrary-android project.
The idea is to remove any hard dependencies over google-cast framework to replace it with ConnectSDK abstraction layer.
This would easy the android integration and development of casting capabilities to any compatible ConnectSDK devices.

## Dependencies
* google-play-services_lib library from the Android SDK (at least version 7.5+)
* android-support-v7-appcompat (version 22 or above)
* android-support-v7-mediarouter (version 22 or above)
* connect-sdk-android-lite (version 1.4 or above)

## Documentation
See the "CastCompanionLibrary.pdf" inside the project for a more extensive documentation.

## References and How to report bugs
* [Cast Developer Documentation](http://developers.google.com/cast/)
* [Design Checklist](http://developers.google.com/cast/docs/design_checklist)
* If you find any issues with this library, please open a bug here on GitHub
* Question are answered on [StackOverflow](http://stackoverflow.com/questions/tagged/google-cast)

## How to make contributions?
any help is gladly appreciated

## License
See LICENSE

## Google+
Google Cast Developers Community on Google+ [http://goo.gl/TPLDxj](http://goo.gl/TPLDxj)

## Change List

1.0.0

 * based on version 2.3 of CastCompanionLibrary-android.
 * the refactoring to make this project works with ConnectSDK introduces a restructured and updated code base that has backward-incompatible changes with the original project
  some functionality are not yet supported (abstracted) by ConnectSDK (like QueueItem in a playList, and changing audio/subtitle track)
