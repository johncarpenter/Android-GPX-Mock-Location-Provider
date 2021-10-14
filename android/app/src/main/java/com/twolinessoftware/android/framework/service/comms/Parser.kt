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
package com.twolinessoftware.android.framework.service.comms

import com.twolinessoftware.android.framework.util.Logger
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

abstract class Parser {
    abstract fun parse(xml: String)
    @Throws(XmlPullParserException::class)
    protected fun buildXmlParser(xml: String?): XmlPullParser? {
        var xpp: XmlPullParser? = null
        val factory = XmlPullParserFactory.newInstance()
        xpp = factory.newPullParser()
        xpp.setInput(StringReader(xml))
        return xpp
    }

    protected fun getNodesFromXml(xml: String, tag: String): String? {
        val startTag = "<$tag"
        val endTag = "</$tag>"
        var response: String? = null
        try {
            response = xml.substring(xml.indexOf(startTag), xml.indexOf(endTag)
                    + endTag.length)
        } catch (se: StringIndexOutOfBoundsException) {
            Logger.e(LOGNAME, "Parser:Invalid XML Tag:$tag")
        }
        return response
    }

    protected fun getTextFromNode(xml: String, tag: String): String? {
        val startTag = "<$tag>"
        val endTag = "</$tag>"
        var response: String? = null
        try {
            response = xml.substring(xml.indexOf(startTag) + startTag.length,
                    xml.indexOf(endTag))
        } catch (se: StringIndexOutOfBoundsException) {
            Logger.e(LOGNAME, "Parser:Invalid XML Tag:$tag")
        }
        return response
    }

    companion object {
        private const val LOGNAME = "Framework.Parser"
    }
}