package com.twolinessoftware.android.framework.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {

	public static enum LEVEL {
		all, none, debug, warn, info, error
	};

	private static LEVEL level = LEVEL.all;
	private static String filename = null;
	private static boolean writeToSD = false;
	private static String LOGNAME = "Framework.Logger";

	public static void setLevel(LEVEL _level) {
		level = _level;
	}

	public static void setFileStorage(String _filename) {
		writeToSD = true;
		filename = _filename;
	}

	public static void disableFileStorage() {
		writeToSD = false;
	}

	public static void d(String tag, String msg) {
		if (level == LEVEL.all || level == LEVEL.debug) {
			android.util.Log.d(tag, msg);
			writeToSDLog("[Debug]"+msg);
		}

	}

	public static void i(String tag, String msg) {
		if (level == LEVEL.all || level == LEVEL.debug || level == LEVEL.warn) {
			android.util.Log.i(tag, msg);
			writeToSDLog("[Info]"+msg);
		}
	}

	public static void w(String tag, String msg) {
		if (level == LEVEL.all || level == LEVEL.debug || level == LEVEL.info
				|| level == LEVEL.warn) {
			android.util.Log.w(tag, msg);
			writeToSDLog("[Warn]"+msg);
		}
	}

	public static void e(String tag, String msg) {
		if (level == LEVEL.all || level == LEVEL.debug) {
			android.util.Log.e(tag, msg);
			writeToSDLog("[Error]"+msg);
		}
	}

	private static void writeToSDLog(String message) {
		try {

			filename = (filename == null) ? "application.log" : filename;

			if (writeToSD) {
				File gpxfile = new File("/sdcard/" + filename);
				if (!gpxfile.exists())
					gpxfile.createNewFile();

				FileWriter gpxwriter = new FileWriter(gpxfile, true);
				PrintWriter out = new PrintWriter(gpxwriter);

				DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				out.println("[" + df.format(new Date()) + "]" + message);
				out.close();
			}

		} catch (IOException e) {
			android.util.Log.e("Logger", "Unable to write debug file "
					+ e.getMessage() + ". Logging has been disabled");
			writeToSD = false;
		}

	}

	public static void writeToHttpLog(String message) {
		try {

			File gpxfile = new File("/sdcard/framework-http.log");
			if (!gpxfile.exists())
				gpxfile.createNewFile();

			FileWriter gpxwriter = new FileWriter(gpxfile, true);
			PrintWriter out = new PrintWriter(gpxwriter);
			out.println(message);
			out.close();

		} catch (IOException e) {
			Logger.e(LOGNAME, "Could not write file " + e.getMessage());
		}

	}

	public static String getStackTrace(Throwable t) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw, true);
		t.printStackTrace(pw);
		pw.flush();
		sw.flush();
		return sw.toString();
	}

	public static String readLog() {
		StringBuffer sb = new StringBuffer();

		try {
			filename = (filename == null) ? "application.log" : filename;
			File logFile = new File("/sdcard/" + filename);
			if (!logFile.exists())
				return "LogFile does not exist. It must be enabled in the FrameworkDefaults.ENABLE_FILELOGGING = true";
			
			FileReader f = new FileReader(logFile);
			BufferedReader in = new BufferedReader(f);

			Boolean end = false;

			while (!end) {
				String s = in.readLine();
				if (s == null){
					end = true;
				}else{
					sb.append(s);
					sb.append("\n");
				}
				
			}
			in.close();
		} catch (Exception ex) {
			Logger.e(LOGNAME, "Could not read file " + ex.getMessage());
		}
		
		return sb.toString();
	}
	
	public static void eraseLogFile(){
		filename = (filename == null) ? "application.log" : filename;
		File logFile = new File("/sdcard/" + filename);
		if (logFile.exists())
			logFile.delete();
	}
	
}
