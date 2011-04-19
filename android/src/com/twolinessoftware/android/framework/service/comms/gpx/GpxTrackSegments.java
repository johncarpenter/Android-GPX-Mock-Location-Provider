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

import java.io.Serializable;
import java.util.ArrayList;

public class GpxTrackSegments implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2136941679164652258L;
	private ArrayList<GpxTrackSegment> trackSegments = new ArrayList<GpxTrackSegment>();

	public void setTrackSegments(ArrayList<GpxTrackSegment> trackSegments) {
		this.trackSegments = trackSegments;
	}

	public ArrayList<GpxTrackSegment> getTrackSegments() {
		return trackSegments;
	}

}
