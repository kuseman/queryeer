
# Queryeer IDE

Query IDE based on [Payloadbuilder](https://github.com/kuseman/payloadbuilder) built on Swing UI.

## Table of Contents

* [Usage](#usage)
* [Developing](#developing)

## Usage

* Download dist and unzip
* Run launcher script in `bin` folder
* Settings/user preferences for editor and plugins are located in `etc`

## Developing

Adding UI extension for Catalog implementations is a matter of implementing [ICatalogExtensionFactory](https://github.com/kuseman/queryeer/blob/master/queryeer-api/src/main/java/se/kuseman/queryeer/editor/api/ICatalogExtensionFactory.java)

Catalog extensions for Queryeer is loaded with it's own isolated classloader so make sure that dependencies are bundled. Using maven assembly plugin is a great choice for this. 
Enabling the plugin by placing the distribution in the `plugins` folder in the unziped Queryeer distribution.
Extra dependencies like jdbc-drivers etc. can be placed in `shared` folder. 


