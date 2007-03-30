package org.omnetpp.common.util;

import java.math.BigDecimal;

public class TimeUtils {
	private static BigDecimal appendToTimeString(StringBuffer timeString, BigDecimal time, String name) {
		long value = time.longValue();
		if (value > 0) {
			timeString.append(value);
			timeString.append(name);
		}
		
		return time.subtract(new BigDecimal(value)).movePointRight(3);
	}

	public static String secondsToTimeString(double time) {
		return secondsToTimeString(new BigDecimal(time));
	}

	public static String secondsToTimeString(BigDecimal time) {
		StringBuffer timeString = new StringBuffer();
		time = appendToTimeString(timeString, time, "s ");
		time = appendToTimeString(timeString, time, "ms ");
		time = appendToTimeString(timeString, time, "us ");
		time = appendToTimeString(timeString, time, "ns ");
		time = appendToTimeString(timeString, time, "ps ");
		time = appendToTimeString(timeString, time, "fs ");
		time = appendToTimeString(timeString, time, "as ");

		if (timeString.length() == 0)
			timeString.append("0s");
		
		return timeString.toString().trim();
	}
	
	public static BigDecimal commonPrefix(double time1, double time2) {
		String s1 = new BigDecimal(time1).toPlainString();
		String s2 = new BigDecimal(time2).toPlainString();
		StringBuffer common = new StringBuffer();

		int len = Math.min(s1.length(), s2.length());
		boolean matches = true;
		boolean decimalPointFound = false;

		for (int i = 0; i < len; i++) {
			if (s1.charAt(i) == '.' || s2.charAt(i) == '.')
				decimalPointFound = true;

			if (matches && s1.charAt(i) == s2.charAt(i))
				common.append(s1.charAt(i));
			else {
				matches = false;

				if (decimalPointFound)
					break;
				else
					common.append('0');
			}
		}

		if (decimalPointFound)
			return new BigDecimal(common.toString());
		else
			return BigDecimal.ZERO;
	}
}
