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
package org.onebusaway.gtfs_realtime.locations_producer_demo;

import java.util.ArrayList;

import javax.inject.Singleton;

import com.mongodb.DBObject;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.google.transit.realtime.GtfsRealtime.Position;

import org.onebusway.gtfs_realtime.exporter.GtfsRealtimeLibrary;

/**
 * A list of the most recent vehicle locations. The list has one element
 * per vehicle. When a new location is inserted, if it corresponds to a 
 * vehicle already in the list, and has a newer timestamp, then the new 
 * location replaces the old. If the new location does not correspond to
 * any existing vehicles, then add it to the list.
 * 
 * @author jlynn
 */
@Singleton
public class LocationList {
    ArrayList<Location> locations = new ArrayList<Location>();

    public void addLocation(Location newLoc) {
	/* New vehicle? Add new Location to list.
	   Old vehicle + new timestamp? Replace old Location w/ new.
	   Old vehicle + old timestamp? Do not add to list.
	 */
	long newTime = newLoc.getTimestamp();
	String newId = newLoc.getId();

	boolean replace = false;
	boolean newVehicle = true;
	int iReplace = 0;

	int listLen = locations.size();
	for (int i = 0; i < listLen; i++) {
	    Location prevLoc = locations.get(i);
	    long prevTime = prevLoc.getTimestamp();
	    String prevId = prevLoc.getId();

	    if (prevId.equals(newId)) {
		newVehicle = false;
		if (prevTime < newTime) {
		    // This bus location replaces a previous one in the feed.
		    iReplace = i;
		    replace = true;
		}
	    }
	}
	if (replace) {
	    // Old vehicle + updated timestamp:
	    locations.set(iReplace, newLoc);
	}
	else if (newVehicle) {
	    // New vehicle:
	    locations.add(newLoc);
	}
	return;
    }

    public FeedMessage getLocationFeedMessage() {
	/**
	 * The FeedMessage.Builder is what we will use to build up 
	 * our GTFS-realtime feed. Add all locations to the feed
	 * and then build and return the result.
	 */
	FeedMessage.Builder feedMessage = 
	    GtfsRealtimeLibrary.createFeedMessageBuilder();
	
	int listLen = locations.size();
	for (int i = 0; i < listLen; i++) {
	    Location newLoc = locations.get(i);
	    FeedEntity.Builder newEnt = newLoc.getFeedEntityBuilder();
	    feedMessage.addEntity(newEnt);
	}

	return feedMessage.build();
    }

    public long maxTime() {
	/**
	 * Returns latest timestamp of any vehicle locations.
	 */
	long maxStamp = 0L;

	int listLen = locations.size();
	for (int i = 0; i < listLen; i++) {
	    Location loc = locations.get(i);
	    long stamp = loc.getTimestamp();
	    if (stamp > maxStamp) {
		maxStamp = stamp;
	    }
	}
	return maxStamp;
    }
}