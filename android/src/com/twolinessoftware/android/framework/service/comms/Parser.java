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
package com.twolinessoftware.android.framework.service.comms;

import java.io.StringReader;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import com.twolinessoftware.android.framework.util.Logger;

public abstract class Parser {

	private static final String LOGNAME = "Framework.Parser";

	public abstract void parse(String xml);

	protected XmlPullParser buildXmlParser(String xml)
			throws XmlPullParserException {
		XmlPullParser xpp = null;

		XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
		xpp = factory.newPullParser();
		xpp.setInput(new StringReader(xml));

		return xpp;
	}

	protected String getNodesFromXml(String xml, String tag) {
		String startTag = "<" + tag;
		String endTag = "</" + tag + ">";

		String response = null;

		try {
			response = xml.substring(xml.indexOf(startTag), xml.indexOf(endTag)
					+ endTag.length());
		} catch (StringIndexOutOfBoundsException se) {
			Logger.e(LOGNAME, "Parser:Invalid XML Tag:" + tag);
		}

		return response;

	}

	protected String getTextFromNode(String xml, String tag) {
		String startTag = "<" + tag + ">";
		String endTag = "</" + tag + ">";

		String response = null;

		try {
			response = xml.substring(xml.indexOf(startTag) + startTag.length(),
					xml.indexOf(endTag));
		} catch (StringIndexOutOfBoundsException se) {
			Logger.e(LOGNAME, "Parser:Invalid XML Tag:" + tag);
		}

		return response;

	}

}
