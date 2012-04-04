Install
=======

Tested with Hudson 1.458

* Upload target/openstack.hpi to your instance of Hudson
* Configure OpenStack profile: Manage Hudson -> Configure System -> OpenStack profiles
* Project -> Configure -> [x] Publish artifacts to OpenStack Storage

Building
========

* From a standalone tree
  * While in the `hudson-openstack` directory, with no parent Hudson source, `mvn` might work
  * Note: you may have to move `dotm2_settings.xml` to `~/.m2/settings.xml`

Notes
=====

* Only the basename of source files is use as the object key name, an option to include the path name relative to the workspace should probably added.

Acknowledgements
================

* The S3 plugin authors for providing a great place to start copy/pasting from
* The OpenStack Java Bindings authors (thanks Luis!)
