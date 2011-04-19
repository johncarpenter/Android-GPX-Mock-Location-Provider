/*
 * Copyright (c) 2011 2linessoftware.com
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twolinessoftware.android.framework.service.comms.gpx;

import com.thoughtworks.xstream.XStream;
import com.twolinessoftware.android.framework.service.comms.Parser;
import com.twolinessoftware.android.framework.service.comms.XStreamParser;

public class GpxParser extends Parser {

	private GpxParserListener listener;

	public GpxParser(GpxParserListener listener) {
		this.listener = listener;
	}

	@Override
	public void parse(String xml) {
		try {
			String data = getNodesFromXml(xml, "trk");

			XStream xstream = XStreamParser.getXStream();
			xstream.alias("trkpt", GpxTrackPoint.class);
			xstream.alias("trkseg", GpxTrackSegment.class);
			xstream.alias("trk", GpxTrackSegments.class);

			xstream.useAttributeFor(GpxTrackPoint.class, "lat");
			xstream.useAttributeFor(GpxTrackPoint.class, "lon");

			xstream.addImplicitCollection(GpxTrackSegment.class, "trackPoints");
			xstream.addImplicitCollection(GpxTrackSegments.class,
					"trackSegments");

			GpxTrackSegments items = (GpxTrackSegments) xstream.fromXML(data);

			if (listener != null)
				listener.onGpxRoute(items);

		} catch (RuntimeException re) {
			if (listener != null)
				listener.onGpxError(re.getMessage());
		}
	}

}
