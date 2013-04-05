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

import java.io.File;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.lang.IllegalArgumentException;

import javax.inject.Inject;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Parser;
import org.onebusaway.cli.CommandLineInterfaceLibrary;
import org.onebusaway.guice.jsr250.LifecycleService;
import org.onebusway.gtfs_realtime.exporter.VehiclePositionsFileWriter;
import org.onebusway.gtfs_realtime.exporter.VehiclePositionsServlet;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

public class GtfsRealtimeLocationsProducerDemoMain {

    private static final String ARG_LOCATIONS_PATH = "locationsPath";

    private static final String ARG_LOCATIONS_URL = "locationsUrl";

    private static final String ARG_MONGO_CLIENT = "mongoClient";
    private static final String ARG_DATABASE_NAME = "dbName";
    private static final String ARG_COLLECTION_NAME = "collectionName";
    
    public static void main(String[] args) throws Exception {
	GtfsRealtimeLocationsProducerDemoMain m = new GtfsRealtimeLocationsProducerDemoMain();
	m.run(args);
    }
    
    private GtfsRealtimeProviderImpl _provider;
    
    private LifecycleService _lifecycleService;
    
    @Inject
	public void setProvider(GtfsRealtimeProviderImpl provider) {
	_provider = provider;
    }
    
    @Inject
	public void setLifecycleService(LifecycleService lifecycleService) {
	_lifecycleService = lifecycleService;
    }

    public void run(String[] args) throws Exception {

	if (args.length == 0 || CommandLineInterfaceLibrary.wantsHelp(args)) {
	    printUsage();
	    System.exit(-1);
	}

	Options options = new Options();
	buildOptions(options);
	Parser parser = new GnuParser();
	CommandLine cli = parser.parse(options, args);

	Set<Module> modules = new HashSet<Module>();
	GtfsRealtimeLocationsProducerDemoModule.addModuleAndDependencies(modules);

	Injector injector = Guice.createInjector(modules);
	injector.injectMembers(this);

	if (cli.hasOption(ARG_MONGO_CLIENT) && 
	    cli.hasOption(ARG_DATABASE_NAME) &&
	    cli.hasOption(ARG_COLLECTION_NAME)) {
	    _provider.setMongo(cli.getOptionValue(ARG_MONGO_CLIENT));
	    _provider.setDB(cli.getOptionValue(ARG_DATABASE_NAME));
	    _provider.setColl(cli.getOptionValue(ARG_COLLECTION_NAME));
	}
	else
	    throw new IllegalArgumentException("Need MongoClient URI, database name, collection name.");

	if (cli.hasOption(ARG_LOCATIONS_URL)) {
	    URL url = new URL(cli.getOptionValue(ARG_LOCATIONS_URL));
	    VehiclePositionsServlet servlet = injector.getInstance(VehiclePositionsServlet.class);
	    servlet.setUrl(url);
	}
	if (cli.hasOption(ARG_LOCATIONS_PATH)) {
	    File path = new File(cli.getOptionValue(ARG_LOCATIONS_PATH));
	    VehiclePositionsFileWriter writer = injector.getInstance(VehiclePositionsFileWriter.class);
	    writer.setPath(path);
	}

	_lifecycleService.start();
    }

    private void printUsage() {
	CommandLineInterfaceLibrary.printUsage(getClass());
    }

    protected void buildOptions(Options options) {
	options.addOption(ARG_LOCATIONS_PATH, true, "trip updates path");
	options.addOption(ARG_LOCATIONS_URL, true, "trip updates url");
	options.addOption(ARG_MONGO_CLIENT, true, "MongoDB URI");
	options.addOption(ARG_DATABASE_NAME, true, "database name");
	options.addOption(ARG_COLLECTION_NAME, true, "collection name");
    }
}
