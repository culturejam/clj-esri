# clj-esri

A Clojure client for Esri ArcGIS.

## Current Status

This project is in early stage development and the API is likely to change.
It also only supports a small subset of the Esri API at this time.

## Getting Started

Include in your project.clj:

```clj
:dependencies [[clj-esri '0.2.4']]
```

Require:

```clj
(require 'clj-esri.core :as 'esri)
```

## Example

```clj
(esri/with-https
  (esri/with-arcgis-config "www.arcgis.com/sharing/rest" ;arcgis-online-endpoint
                           "services1.arcgis.com/yourorgid/arcgis/rest/services" ;arcgis-server-endpoint
    ;You probably want to cache your access-token
    (let [access-token (:token (esri/generate-token esri-username esri-password "referer"))]
      (esri/with-token access-token
          (let [response-get-services (esri/get-services)]
            (print "Get services: " response-get-services)))))
```

## Authors

[Ticean Bennett](https://github.com/ticean)

Many thanks to [Matt Reville](https://github.com/mattrepl) of Lightpost Software.
The design of [clojure-twitter](https://github.com/mattrepl/clojure-twitter) greatly influenced this project.


## License

Copyright © 2013 Culture Jam, Inc.

Distributed under the Eclipse Public License, the same as Clojure.
