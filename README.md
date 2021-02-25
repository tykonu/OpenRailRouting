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
* a lot of features which would need data which is not in OSM (incline, structure gauges)


## Building

This project uses Maven 3.x for building. The forked GraphHopper is provided as a Git submodule.

```sh
git submodule init
git submodule update
bash build.sh
```

JUnit 4.x is used for unit tests.

## Configuration

Configuration happens via a YAML file which is given as a positional parameter
when starting the routing engine.  Most parts of the configuration are
identical to GraphHopper. However, one part is different – the flag encoders
(aka routing profiles). You can define custom flag encoders which define how
various properties of the tracks are encoded in the graph and which ones make it
up into the graph at all depending on OSM tags `railway=*`, `electrification=*`,
`gauge=*`, `voltage=*` and `frequency=*`.

The remaining parts of the documentation are equal to standard GraphHopper configuration.
See config-example.yml for details.


## Running

### Importing a Graph

To import a graph for the routing engine, execute

```sh
java -Xmx2500m -Xms50m -jar target/railway_routing-0.0.1-SNAPSHOT-jar-with-dependencies.jar import [NAMED_ARGS] CONFIG_FILE OSM_FILE
```

Named Arguments:

* `--graph_location PATH`: Path to graph directory, overwrites the value of
  graphhopper.graph.location set in your configuration file.
* `--profiles PROFILES`: Define profiles to be used, use the name of the flag encoders. Use a
  comma to separate multiple values. Usage of shortest weighting and a U-turn time of 30 minutes
  is hard-coded.

Positional Arguments:

* CONFIG_FILE: path to configuration file in YAML format
* OSM_FILE: path to OSM raw data file in .osm.pbf format


### API/Server

To serve the API (routing and map matching), execute

```sh
java -Xmx2500m -Xms50m -jar target/railway_routing-0.0.1-SNAPSHOT-jar-with-dependencies.jar server CONFIG_FILE
```

Positional Arguments:

* CONFIG_FILE: path to configuration file in YAML format

You can overwrite settings in the configuration file if they are defined there (at least as an empty string) using Java system properties.

For example, if you want to use a different graph directory than the one defined in the configuration file, use `-Ddw.graphhopper.graph.location=NEW_PATH`.
Mind to prepend `-Ddw.` and that the Java system properties have to be defined before the `-jar` option.

### Match

You can match a one or many GPX files with the graph without starting the API service ("server" command):

```sh
java -Xmx2500m -Xms50m -jar target/railway_routing-0.0.1-SNAPSHOT-jar-with-dependencies.jar match [NAMED_ARGS] CONFIG_FILE
```

Named Arguments:

* `-V VEHICLE`, `--vehicle VEHICLE`: Routing profile to be used
* `-a VALUE`, `--gps-accuracy VALUE`: GPS accuracy in meter (float value)
* `--max-nodes VALUE`: Maximum number of nodes to visit between two trackpoints (default: 10000)
* `--graph_location PATH`: Path to graph directory
* `--gpx-location PATH_OR_PATTERN`: Path to input GPX file or glob pattern to input GPX files

Positional Arguments:

* CONFIG_FILE: path to configuration file in YAML format

Output files will be in GPX format and named like this: `INPUT_PATH.res.gpx`

## License

See [LICENSE.txt](LICENSE.txt)

See [THIRD_PARTY.md](THIRD_PARTY.md) for a list of all third-party code in this repository

## Thank you

Development of this project has been supported by [Geofabrik](https://www.geofabrik.de) and [SNCF](https://www.sncf.com/fr).
