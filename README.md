# OpenStreetMap based routing on railway tracks

This is a prototype of a routing engine for railways based on a forked version of the
[GraphHopper](https://github.com/graphhopper/graphhopper) routing engine and OpenStreetMap data.

Following features are currently supported:

* simple routing requests
* map matching
* taking turn angles into account (to avoid U-turns on points)
* disabling turns on railway crossings (OSM tag `railway=railway_crossing`)
* using only tracks which have a compatible gauge
* not using tracks without catenary if it is an electrical locomotive/electrical multiple unit
* distinction between third rails and catenary
* support of tracks with multiple gauges
* support of tracks with switchable voltage and frequency

Lacking features:

* avoiding the usage of the opposite track (on double-track lines)
* taking the low acceleration and the long breaking distances of trains into account
* many profiles
* a lot of features which would need data which is not in OSM (incline, structure gauges)


## Building

This project uses Maven 3.x for building. The forked GraphHopper is provided as a Git submodule.

```sh
git submodule init
bash build.sh
```

JUnit 4.x is used for unit tests.

## Running

To run the routing engine, execute

```sh
java -Xmx2500m -Xms50m -Dgraphhopper.prepare.ch.weightings=no \
  -Dgraphhopper.datareader.file=$OSMFILE -Dgraphhopper.profiles=freight_diesel \
  -jar target/railway_routing-0.0.1-SNAPSHOT-jar-with-dependencies.jar $ACTION $CONFIG_FILE $OPTARG
```

The tool currently supports three different actions (`$ACTION` above):

* `import` to import the graph (graph will be stored at the subdirectory `graph-cache/`)
* `web` to run the web interface on the port specified in a YAML configuration file (see
  `config.yml` as an example)
* `match` do map matching. This command needs additional arguments called `$OPTARG` above.

All commands have some arguments to be handed over as Java system variables using the `-Dkey=value`
option of the JVM. These arguments can also be given using the YAML file.

### Import

Required arguments:

* `-Dgraphhopper.datareader.file=$PATH`: path to OSM file
* `-Dgraphhopper.graph.location=./graph-cache`: directory where the graph should be written to
  (default: `./graph-cache`)

Optional arguments:

* `-Dgraphhopper.profiles=freight_electric_15kvac_25kvac,freight_diesel,tgv_15kvac25kvac1.5kvdc,tgv_25kvac1.5kvdc3kvdc`:
  flag encoders to be used. Following encoders are available:
  * `freight_electric_15kvac_25kvac`
  * `freight_diesel`
  * `tgv_15kvac25kvac1.5kvdc`
  * `tgv_25kvac1.5kvdc3kvdc`
  * `freight_electric_25kvac1.5kvdc3kvdc`

### Web

Required arguments:

* `-Dgraphhopper.datareader.file=$PATH`: path to OSM file
* `-Dgraphhopper.graph.location=./graph-cache`: directory where the graph should be read from
  (default: `./graph-cache`)
* `-Dserver.applicationConnector.port=$PORT`: port to be opened by Jetty
* `-Dgraphhopper.profiles=<flag_encoders>`: this must be the same as used for the import

### Match

Required arguments:

* `-Dgraphhopper.datareader.file=$PATH`: path to OSM file
* `-Dgraphhopper.graph.location=./graph-cache`: directory where the graph should be read from
  (default: `./graph-cache`)

Followoing arguments have to be provided (not as Java system variables). You can retriev this list
by calling
`java -jar target/railway_routing-0.0.1-SNAPSHOT-jar-with-dependencies.jar match config.yml`

* `--gpx-location=$PATTERN` is required. This can be either a single GPX file or a wildcard pattern
  like `/path/to/dir/mytracks/*.gpx`. The resulting routes will be written as GPX files to same
  directory but `.res.gpx` will be appended to their file names.
* `-V VEHICLE`, `--vehicle=$VEHICLE`: routing profile to be used.

Optional arguments:

* `-a NUMBER`, `--gps-accuracy=NUMBER`: GPS accuracy in metres (default: 40)
* `--max_nodes=NUMBER`: maximum number of nodes to visit between two trackpoints (default: 10,000)

## License

see [LICENSE.txt](LICENSE.txt)

## Thank you

Development of this project has been supported by [Geofabrik](https://www.geofabrik.de) and [SNCF](https://www.sncf.com/fr).
