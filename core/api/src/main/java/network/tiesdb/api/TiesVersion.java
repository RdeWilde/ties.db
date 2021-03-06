/*
 * Copyright 2017 Ties BV
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package network.tiesdb.api;

/**
 * TiesDB version API.
 * 
 * <P>Defines common version functions.
 *  
 * @author Anton Filatov (filatov@ties.network)
 */
public interface TiesVersion {

	static TiesVersion current = TiesApiVersion.v_0_1_0_alpha;

	static final class ToString {
		public static String format(TiesVersion version) {
			return format(version, version.getApiVersion());
		}

		private static String format(TiesVersion version, TiesApiVersion apiVersion) {
			return version.getMajorVersion() + "." + version.getMinorVersion() + "." + version.getIncrementalVersion()
					+ (null != version.getQualifer() ? "." + version.getQualifer() : "")
					+ (!version.equals(apiVersion) ? "(" + format(apiVersion, apiVersion) + ")" : "");
		}
	}

	Integer getMajorVersion();

	Integer getMinorVersion();

	Integer getIncrementalVersion();

	String getQualifer();

	TiesApiVersion getApiVersion();

}
