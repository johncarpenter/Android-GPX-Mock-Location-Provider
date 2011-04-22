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

import java.io.StringReader;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import com.twolinessoftware.android.framework.service.comms.Parser;

public class GpxSaxParser extends Parser {

	private GpxSaxParserListener listener;

	public GpxSaxParser(GpxSaxParserListener listener) {
		this.listener = listener;
	}

	@Override
	public void parse(String xml) {

		try {

			String data = getNodesFromXml(xml, "trk");

			SAXParserFactory spf = SAXParserFactory.newInstance();
			SAXParser sp = spf.newSAXParser();

			XMLReader xr = sp.getXMLReader();
			xr.setFeature("http://xml.org/sax/features/namespaces", false);
			/* Create a new ContentHandler and apply it to the XML-Reader */
			GpxHandler gpxHandler = new GpxHandler();
			xr.setContentHandler(gpxHandler);

			xr.parse(new InputSource(new StringReader(data)));
		

		} catch (Exception e) {
			if (listener != null)
				listener.onGpxError(e.getMessage());

		}
	}

	class GpxHandler extends DefaultHandler {

		private GpxTrackPoint point;
		private String currentTag;

		@Override
		public void startDocument() throws SAXException {
			if (listener != null)
				listener.onGpxStart();
		}

		@Override
		public void characters(char[] ch, int start, int length)
				throws SAXException {
			if (currentTag != null) {
				String value = new String(ch, start, length);

				if (currentTag.equalsIgnoreCase("ele"))
					point.setEle(Float.parseFloat(value));
				else if (currentTag.equalsIgnoreCase("time"))
					point.setTime(value);
				else if (currentTag.equalsIgnoreCase("sat"))
					point.setSat(value);
				else if (currentTag.equalsIgnoreCase("fix"))
					point.setFix(value);
			}
		}

		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) throws SAXException {
			if (qName.equalsIgnoreCase("trkpt")) {
				point = new GpxTrackPoint();
				point.setLat(Float.parseFloat(attributes.getValue("lat")));
				point.setLon(Float.parseFloat(attributes.getValue("lon")));

			}

			currentTag = qName;
		}

		@Override
		public void endElement(String uri, String localName, String qName)
				throws SAXException {

			currentTag = null;
			if (qName.equalsIgnoreCase("trkpt")) {
				if (listener != null)
					listener.onGpxPoint(point);
			}
		}

		@Override
		public void endDocument() throws SAXException {
			if (listener != null)
				listener.onGpxEnd();
		}

	}

}
