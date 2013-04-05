mongodb-to-onebusaway-gtfs-realtime-feed
========================================

Uses OneBusAway backend to convert MongoDB data into a GTFS-realtime vehicle locations feed.

## Background

This app is primarily based on Brian Ferris's demo code [here](https://github.com/OneBusAway/onebusaway-gtfs-realtime-alerts-producer-demo) which converts SEPTA alerts into a GTFS-realtime feed. I've modified it to pull data from a MongoDB instance, to be specified by the end user. The code assumes that the data has a particular structure. Specifically, the MongoDB data elements must have (minimally) the following JSON structure:

```
{
  "entity": {
    "id": String,
    "vehicle": {
      "timestamp": long,
      "position": {
        "bearing": float,
        "longitude": float,
        "latitude": float,
        "speed": float
      }
    }
  }
}
```

## Building

After cloning the repository, run
```
mvn package
```
in root directory. Maven should resolve dependencies.

## Running

From root:

```
java -jar target/onebusaway-gtfs-realtime-locations-producer-demo-0.0.1-SNAPSHOT.jar --locationsUrl=URL --mongoClient=MongoDB_URI --dbName=database_name --collectionName=collection_name
```

To demo locally, use e.g. http://localhost:8080/locations as the locationsURL. A human-readable version of the GTFS-realtime feed will be accessible at http://localhost:8080/locations?debug. Alternatively the user may specify a file with `--locationsPath=path` to which the GTFS-realtime feed will be written.
