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
package com.twolinessoftware.android.framework.service.comms.gpx

import com.twolinessoftware.android.framework.service.comms.Parser
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

class GpxSaxParser(private val listener: GpxSaxParserListener?) : Parser() {
    override fun parse(xml: String) {
        try {
            val data = getNodesFromXml(xml, "trk")
            val spf = SAXParserFactory.newInstance()
            val sp = spf.newSAXParser()
            val xr = sp.xmlReader
            xr.setFeature("http://xml.org/sax/features/namespaces", false)
            /* Create a new ContentHandler and apply it to the XML-Reader */
            val gpxHandler = GpxHandler()
            xr.contentHandler = gpxHandler
            xr.parse(InputSource(StringReader(data)))
        } catch (e: Exception) {
            listener?.onGpxError(e.message)
        }
    }

    internal inner class GpxHandler : DefaultHandler() {
        private var point: GpxRawTrackPoint? = null
        private var currentTag: String? = null
        @Throws(SAXException::class)
        override fun startDocument() {
            listener?.onGpxStart()
        }

        @Throws(SAXException::class)
        override fun characters(ch: CharArray, start: Int, length: Int) {
            point?.apply {
                if (currentTag != null) {
                    val value = String(ch, start, length)
                    if (currentTag.equals("ele", ignoreCase = true)) { this.ele = (this.ele ?: "") + value }
                    else if (currentTag.equals("time", ignoreCase = true)) { this.time = (this.time ?: "") + value }
                    else if (currentTag.equals("sat", ignoreCase = true)) { this.sat = (this.sat ?: "") +  value }
                    else if (currentTag.equals("fix", ignoreCase = true)) { this.fix = (this.fix ?: "") + value }
                    else if (currentTag.equals("course", ignoreCase = true)) { this.heading = (this.heading ?: "") + value }
                    else if (currentTag.equals("speed", ignoreCase = true)) { this.speed = (this.speed ?: "") + value }
                }
            }

        }

        @Throws(SAXException::class)
        override fun startElement(uri: String, localName: String, qName: String,
                                  attributes: Attributes) {
            if (qName.equals("trkpt", ignoreCase = true)) {
                point = GpxRawTrackPoint(attributes.getValue("lat").toFloat().toDouble(),
                        attributes.getValue("lon").toFloat().toDouble())
            }
            currentTag = qName
        }

        @Throws(SAXException::class)
        override fun endElement(uri: String, localName: String, qName: String) {
            currentTag = null
            point?.let {
                if (qName.equals("trkpt", ignoreCase = true)) {
                    listener?.onGpxPoint(it.toGpxTrackPoint())
                }
            }
        }

        @Throws(SAXException::class)
        override fun endDocument() {
            listener?.onGpxEnd()
        }
    }
}