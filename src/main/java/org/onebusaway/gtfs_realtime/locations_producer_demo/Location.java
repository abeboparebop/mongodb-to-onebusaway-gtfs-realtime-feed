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

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.google.transit.realtime.GtfsRealtime.Position;

/**
 * A simple Java bean describing one vehicle's location.
 * 
 * @author jlynn
 */
public class Location {

    private final String id;

    private final float latitude;
    private final float longitude;
    private final float bearing;
    private final float speed;

    private final long timestamp;

    public Location(String id, float latitude, float longitude, 
		    float bearing, float speed, long timestamp) {
	this.id = id;
	this.latitude = latitude;
	this.longitude = longitude;
	this.bearing = bearing;
	this.speed = speed;
	this.timestamp = timestamp;
    }

    public Location(DBObject obj) {
	/* Inspect the DBObject to make sure it has the right fields,
	   then use default constructor. */

	DBObject ent = (DBObject) obj.get("entity");
	DBObject veh = (DBObject) ent.get("vehicle");
	DBObject pos = (DBObject) veh.get("position");
	    
	String id = (String) ent.get("id");

	float latitude = (float) objectToDouble(pos.get("latitude"));
	float longitude = (float) objectToDouble(pos.get("longitude"));
	float bearing = (float) objectToDouble(pos.get("bearing"));
	float speed = (float) objectToDouble(pos.get("speed"));

	long timestamp = (long) objectToDouble(veh.get("timestamp"));

	this.id = id;
	this.latitude = latitude;
	this.longitude = longitude;
	this.bearing = bearing;
	this.speed = speed;
	this.timestamp = timestamp;
    }

    public String getId() {
	return id;
    }

    public float getLatitude() {
	return latitude;
    }
    public float getLongitude() {
	return longitude;
    }
    public float getBearing() {
	return bearing;
    }
    public float getSpeed() {
	return speed;
    }
    public long getTimestamp() {
	return timestamp;
    }

    public FeedEntity.Builder getFeedEntityBuilder() {
	/**
	 * Create FeedEntity.Builder for constructing the actual GTFS-realtime
	 * locations feed from the location details.
	 */
	FeedEntity.Builder new_ent = FeedEntity.newBuilder();
	   
	/* Basic structure for a location FeedEntity:

	   FeedEntity {
	     String id
	     VehiclePosition {
	       Position {
	         float latitude
		 float longitude
		 float bearing
		 float speed
	       }
	       long timestamp
	       VehicleDescriptor {
	         String id
	       }
	     }
	   }

	 */
	new_ent.setId(id);
	    
	VehiclePosition.Builder new_vpos = VehiclePosition.newBuilder();
	Position.Builder new_pos = Position.newBuilder();

	new_pos.setLatitude(latitude);
	new_pos.setLongitude(longitude);
	new_pos.setBearing(bearing);
	new_pos.setSpeed(speed);
	    
	new_vpos.setPosition(new_pos);

	VehicleDescriptor.Builder new_vd = VehicleDescriptor.newBuilder();
	new_vd.setId(id);

	// Divide by 1000 b/c MongoDB feed arrives in ms, while Google's 
	// protobuf wants seconds.
	long t1 = (long) (((double) timestamp)/1000.0);

	new_vpos.setTimestamp(t1);
	new_vpos.setVehicle(new_vd);

	new_ent.setVehicle(new_vpos);

	return new_ent;
    }

    private double objectToDouble(Object obj) {
	String str = obj.toString();
	double d = Double.valueOf(str).doubleValue();
	return d;
    }
}