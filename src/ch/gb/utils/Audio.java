/* 
 * Copyright (C) 2017 bluew
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ch.gb.utils;

public class Audio {
	private static float lastAmp=0;
	private static float lastOut=0;

	//Beannich
	/**
	 * Block DC component of an audio signal
	 * @param sample
	 * @return
	 */
	public static float blockDC(float sample) {
		float output = sample - lastAmp + 0.999f * lastOut;
		lastAmp = sample;
		lastOut = output;

		return output;
	}
	public static float runLowpass(){
		return 0f;
	}
}
