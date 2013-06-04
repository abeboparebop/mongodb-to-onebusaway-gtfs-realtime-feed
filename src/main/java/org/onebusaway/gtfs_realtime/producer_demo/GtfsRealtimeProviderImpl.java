/**
 * Copyright (C) 2012 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.gtfs_realtime.producer_demo;

import java.lang.Object;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.rmi.UnknownHostException;
import java.lang.IllegalArgumentException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.codec.binary.Hex;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.onebusway.gtfs_realtime.exporter.GtfsRealtimeExporterModule;
import org.onebusway.gtfs_realtime.exporter.GtfsRealtimeProvider;
import org.onebusway.gtfs_realtime.exporter.GtfsRealtimeLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.transit.realtime.GtfsRealtime.EntitySelector;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.google.transit.realtime.GtfsRealtime.Position;


import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoException;
import com.mongodb.WriteConcern;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.DBCursor;
import com.mongodb.ServerAddress;

/**
 * This class produces GTFS-realtime alerts by periodically polling the custom
 * SEPTA alerts API and converting the resulting alert data into the
 * GTFS-realtime format.
 * 
 * Since this class implements {@link GtfsRealtimeProvider}, it will
 * automatically be queried by the {@link GtfsRealtimeExporterModule} to export
 * the GTFS-realtime feeds to file or to host them using a simple web-server, as
 * configured by the client.
 * 
 * @author bdferris
 * 
 */

/**
 * Former description now out of date! Now the class periodically polls a 
 * MongoDB of bus locations and converts that data into GTFS-realtime format.
 *
 * @auther Jacob Lynn
 */

@Singleton
public class GtfsRealtimeProviderImpl implements GtfsRealtimeProvider {

    private static final Logger _log = LoggerFactory.getLogger(GtfsRealtimeProviderImpl.class);
    
    private ScheduledExecutorService _executor;
    
    private volatile FeedMessage _locs = GtfsRealtimeLibrary.createFeedMessageBuilder().build();

    private URL _url;

    private long _currtime = 1365614681000L;

    /**
     * How often alerts will be downloaded, in seconds.
     */
    private int _refreshInterval = 30;

    /**
     * How often bus list will be downloaded, in seconds.
     */
    private int _busListInterval = 60*60*3;

    /**
     * Age above which locations will be removed from list, in ms.
     */
    private long _ageLim = 10*60*1000;

    private MongoClient _client;
    private DB _db;
    private DBCollection _coll;
    private DBCollection _busColl;

    private LocationList locationList = new LocationList();

    /**
     * @param url is a string pointing to our MongoDB (w/ authentication)
     */
    public void setMongo(String url) throws IOException {
	MongoClientURI uri = new MongoClientURI(url);
	System.out.println(uri);
	_client = new MongoClient(uri);
    }
    public void setDB(String db) {
	_db = _client.getDB(db);
    }
    public void setColl(String coll) {
	_coll = _db.getCollection(coll);
    }
    public void setBusColl(String busColl) {
	_busColl = _db.getCollection(busColl);
    }
    public void setAgeLim(int ageLim) {
	_ageLim = ageLim;
    }

    /**
     * @param refreshInterval how often alerts will be downloaded, in seconds.
     */
    public void setRefreshInterval(int refreshInterval) {
	_refreshInterval = refreshInterval;
    }
    
    /**
     * The start method automatically starts up a recurring task that periodically
     * downloads the latest alerts from the SEPTA alerts stream and processes
     * them.
     */
    @PostConstruct
	public void start() {
	_executor = Executors.newSingleThreadScheduledExecutor();
	_log.info("starting GTFS-realtime service");
	_executor.scheduleAtFixedRate(new BusListRefreshTask(), 0, _busListInterval,
				      TimeUnit.SECONDS);
	_executor.scheduleAtFixedRate(new LocationRefreshTask(), 0, _refreshInterval,
				      TimeUnit.SECONDS);
    }

    /**
     * The stop method cancels the recurring alert downloader task.
     */
    @PreDestroy
	public void stop() {
	_log.info("stopping GTFS-realtime service");
	_executor.shutdownNow();
    }

    
    /****
     * {@link GtfsRealtimeProvider} Interface
     ****/
    
    /**
     * We don't care about trip updates, so we return an empty feed here.
     */
    @Override
	public FeedMessage getTripUpdates() {
	FeedMessage.Builder feedMessage = GtfsRealtimeLibrary.createFeedMessageBuilder();
	return feedMessage.build();
    }
    
    /**
     * Return vehicle positions.
     */
    @Override
	public FeedMessage getVehiclePositions() {
	return _locs;
    }
    
    /**
     * We don't care about alerts, so we return an empty feed here.
     */
    @Override
	public FeedMessage getAlerts() {
	FeedMessage.Builder feedMessage = GtfsRealtimeLibrary.createFeedMessageBuilder();
	return feedMessage.build();
    }
    
    /****
     * Private Methods - Here is where the real work happens
     ****/
    
    /**
     * This method downloads the latest alerts, processes each alert in turn, and
     * create a GTFS-realtime feed of alerts as a result.
     */
    private void refreshLocations() throws IOException {
	
	/**
	 * We download the locations as an array of DBObjects.
	 */
	ArrayList<DBObject> dbList = downloadLocations();
	
	/**
	 * We iterate over every DBObject returned by the MongoDB query,
	 * turn them into a Location, and add them to locationList (though
	 * addLocation() only adds a Location to the list if it is actually
	 * new).
	 */
	for (int i = 0; i < dbList.size(); ++i) {
	    DBObject obj = dbList.get(i);
	    Location newLoc = new Location(obj);
	    locationList.addLocation(newLoc);
	}

	// locationList.clearOld(_ageLim);
	
	/**
	 * Build out the final GTFS-realtime feed message and save it to the alerts
	 * field.
	 */
	_locs = locationList.getLocationFeedMessage();
	_log.info("locs extracted: " + _locs.getEntityCount());

	/* Update current time for subsequent queries: */
	_currtime = locationList.maxTime();
    }
    
    /**
     * @return a DBObject array of recent entries in MongoDB collection.
     */
    private ArrayList<DBObject> downloadLocations() throws IOException {
	// get list of distinct bus ids:
	// _log.info("getting distinct bus IDs");
	// List busIDs = _coll.distinct("entity.id");
	// _log.info("success: bus IDs");
	// System.out.println(busIDs);

	ArrayList<DBObject> myList = new ArrayList<DBObject>();
	ArrayList<Object> busIDs = locationList.getBusIDs();

	// Loop over bus ids; get most recent timestamp for each
	for (Object busID : busIDs) {
	    ArrayList queryList = new ArrayList();
	    // most recent timestamp:
	    queryList.add(new BasicDBObject("entity.vehicle.timestamp", 
					    new BasicDBObject("$gt",_currtime)));

	    // match on bus ID:
	    queryList.add(new BasicDBObject("entity.id",busID.toString()));

	    // build full query
	    BasicDBObject query = new BasicDBObject("$and", queryList);

	    _log.info("query on busID " + busID.toString());
	    DBCursor cursor2 = _coll.find(query)
		.sort( new BasicDBObject("entity.vehicle.timestamp", -1))
		.limit(1);

	    try {
		while (cursor2.hasNext()) {
		    DBObject myDoc = cursor2.next();
		    myList.add(myDoc);
		}
	    } finally {
		cursor2.close();
	    }
	    
	}

	return myList;
    }

    // /**
    //  * @return a DBObject array of recent entries in MongoDB collection.
    //  */
    // private getActiveBuses() throws IOException {
    // 	_db.getCollection(coll)
    // 	// get list of distinct bus ids:
    // 	List busIDs = _coll.distinct("entity.id");

    // 	ArrayList<DBObject> myList = new ArrayList<DBObject>();

    // 	// Loop over bus ids; get most recent timestamp for each
    // 	for (Object busID : busIDs) {
    // 	    ArrayList queryList = new ArrayList();
    // 	    // most recent timestamp:
    // 	    queryList.add(new BasicDBObject("entity.vehicle.timestamp", 
    // 					    new BasicDBObject("$gt",_currtime)));

    // 	    // match on bus ID:
    // 	    queryList.add(new BasicDBObject("entity.id",busID.toString()));

    // 	    // build full query
    // 	    BasicDBObject query = new BasicDBObject("$and", queryList);
    // 	    DBCursor cursor2 = _coll.find(query)
    // 		.sort( new BasicDBObject("entity.vehicle.timestamp", -1))
    // 		.limit(1);

    // 	    try {
    // 		while (cursor2.hasNext()) {
    // 		    DBObject myDoc = cursor2.next();
    // 		    myList.add(myDoc);
    // 		}
    // 	    } finally {
    // 		cursor2.close();
    // 	    }
	    
    // 	}

    // 	return myList;
    // }
    
    /**
     * Task that will download new locations from the remote data source when
     * executed.
     */
    private class LocationRefreshTask implements Runnable {
	
	@Override
	    public void run() {
	    try {
		_log.info("refreshing locations");
		refreshLocations();
	    } catch (Exception ex) {
		_log.warn("Error in location refresh task", ex);
	    }
	}
    }

    /**
     * Task that will download bus IDs from the remote data source when
     * executed.
     */
    private class BusListRefreshTask implements Runnable {
	
	@Override
	    public void run() {
	    try {
		// get list of distinct bus ids:
		_log.info("getting distinct bus IDs");
		List busIDs = _coll.distinct("entity.id");
		_log.info("success: bus IDs");
		System.out.println(busIDs);

		locationList.setBusIDs(new ArrayList(busIDs));
	    } catch (Exception ex) {
		_log.warn("Error in bus ID refresh task", ex);
	    }
	}
    }

    /**
     * Convert a (numeric) string to a double.
     */
    private double objectToDouble(Object obj) {
	String str = obj.toString();
	double d = Double.valueOf(str).doubleValue();
	return d;
    }
    
}
