[![Actions Status](https://github.com/kuseman/queryeer/workflows/Java%20CI%20with%20Maven/badge.svg)](https://github.com/kuseman/queryeer/actions)

# Queryeer IDE

Query IDE based on [Payloadbuilder](https://github.com/kuseman/payloadbuilder) built on Swing UI.

## Table of Contents

* [About](#about)
* [Usage](#usage)
* [Developing](#developing)
* [License](#license)

## About

Extensible Query IDE with support for Payloadbuilder Catalogs.

![Queryeer](/documentation/queryeer.png?raw=true "Queryeer")

## Usage

- Download dist and unzip
- Run launcher script in `bin` folder
- Distribution folder layout
  - bin  (start scripts etc.)
  - etc (configuration files etc.)
  - lib (Queryeer libraries)
  - plugins (Plugins)
  - shared (Shared libraries folder. Usefull for jdbc drviers etc. This is shared among all plugins classloaders)

## Developing

Extending Queryeer can be made with a set of extension points.

  - [ICatalogExtensionFactory](https://github.com/kuseman/queryeer/tree/master/queryeer-api/src/main/java/com/queryeer/api/extensions/catalog/ICatalogExtensionFactory.java)
    - Adding a payloadbuilder catalog to Queeryer is made here

   - [IConfigurable](https://github.com/kuseman/queryeer/tree/master/queryeer-api/src/main/java/com/queryeer/api/extensions/IConfigurable.java)
     - Adding a configurable component to Queryeer is made here. This will enable a component to appear in Options dialog to allow for changing and persisting preferences to other compoenents such as Catalog extensions etc.

   - [IOutputExtension](https://github.com/kuseman/queryeer/tree/master/queryeer-api/src/main/java/com/queryeer/api/extensions/output/IOutputExtension.java)
     - Adding an output extension is made here. To enable other types of UI output (like table and text) this is the extension to add. Queryeer comes bundled with Table/Text and File outputs.

   - [IOutputFormatExtension](https://github.com/kuseman/queryeer/tree/master/queryeer-api/src/main/java/com/queryeer/api/extensions/output/IOutputFormatExtension.java)
     - Adding an output format extension is made here. To enable other types of output formats (like JSON, CSV) this is the extension to add. Queryeer comes bundled with JSON and CSV formats.

Extension classes implementing one of the above interfaces or annotated with [Inject](https://github.com/kuseman/queryeer/tree/master/queryeer-api/src/main/java/com/queryeer/api/extensions/Inject.java) is automatically discovered when placed in `plugins` folder. A set of [services](https://github.com/kuseman/queryeer/tree/master/queryeer-api/src/main/java/com/queryeer/api/service) can be injected through constructor injection. Extension classes should have a single constructor or a constructor annotated with  [Inject](https://github.com/kuseman/queryeer/tree/master/queryeer-api/src/main/java/com/queryeer/api/extensions/Inject.java)

Each plugin is loaded with it's own isolated classloader so make sure that dependencies are bundled. Using maven assembly plugin is a great choice for this.

Enabling the plugin by placing the plugin distribution in the `plugins` folder in the unziped Queryeer distribution.
Extra dependencies like jdbc-drivers etc. can be placed in `shared` folder. 

## License

Distributed under the Apache License Version 2.0.

Free for non commercial use