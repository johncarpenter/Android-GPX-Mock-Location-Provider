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
package test.com.twolinessoftware.android.framework.comms.gpx;

import android.test.AndroidTestCase;

import com.twolinessoftware.android.framework.service.comms.gpx.GpxParser;
import com.twolinessoftware.android.framework.service.comms.gpx.GpxParserListener;
import com.twolinessoftware.android.framework.service.comms.gpx.GpxSaxParser;
import com.twolinessoftware.android.framework.service.comms.gpx.GpxSaxParserListener;
import com.twolinessoftware.android.framework.service.comms.gpx.GpxTrackPoint;
import com.twolinessoftware.android.framework.service.comms.gpx.GpxTrackSegments;

public class GpxParserTest extends AndroidTestCase {

	private static String xml = "<?xml version='1.0' encoding='UTF-8' ?><gpx version='1.1' creator='SpeedProof/' xmlns='http://www.topografix.com/GPX/1/1' xmlns:rmc='urn:net:trekbuddy:1.0:nmea:rmc'>	<trk>		<trkseg>			<trkpt lat='51.05197012424469' lon='-114.08636569976807'>				<ele>1048.0</ele>				<time>2009-05-09T19:26:23Z</time>				<fix>2d</fix>				<sat>?</sat>				<extensions>					<rmc:course>0.0</rmc:course>					<rmc:course2>360</rmc:course2>					<rmc:speed>0.0</rmc:speed>				</extensions>			</trkpt>			<extensions>				<tamper name='hash'>d936a44118a439f7c9690003303ebf20bab32f05</tamper>			</extensions>			<trkpt lat='51.05197012424469' lon='-114.08636569976807'>				<ele>1048.0</ele>				<time>2009-05-09T19:26:23Z</time>				<fix>2d</fix>				<sat>?</sat>				<extensions>					<rmc:course>0.0</rmc:course>					<rmc:course2>360</rmc:course2>					<rmc:speed>0.0</rmc:speed>				</extensions>			</trkpt>			<extensions>				<tamper name='hash'>63ab007322a759fb1710cf372cbec565fc26ce4c</tamper>			</extensions>			<trkpt lat='51.05196475982666' lon='-114.086354970932'>				<ele>1047.0</ele>				<time>2009-05-09T19:26:24Z</time>				<fix>2d</fix>				<sat>?</sat>				<extensions>					<rmc:course>0.0</rmc:course>					<rmc:course2>360</rmc:course2>					<rmc:speed>0.0</rmc:speed>				</extensions>			</trkpt>		</trkseg>	</trk></gpx>";

	private int count;

	public void testCorrectParserWithString() {

		GpxParser parser = new GpxParser(new GpxParserListener() {

			@Override
			public void onGpxRoute(GpxTrackSegments items) {
				assertNotNull(items);
			}

			@Override
			public void onGpxError(String message) {
				fail(message);

			}

		});
		parser.parse(xml);

	}

	public void testAsyncCorrectParserWithString() {

		count = 0;

		GpxSaxParser parser = new GpxSaxParser(new GpxSaxParserListener() {

			@Override
			public void onGpxError(String message) {
				fail(message);
			}

			@Override
			public void onGpxPoint(GpxTrackPoint item) {
				count++;
				assertNotNull(item);
			}

			@Override
			public void onGpxStart() {
				count = 0;
			}

			@Override
			public void onGpxEnd() {
				assertEquals(count, 3);
			}

		});
		parser.parse(xml);

	}

}
