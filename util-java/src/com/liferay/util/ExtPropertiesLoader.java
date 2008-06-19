/**
 * Copyright (c) 2000-2008 Liferay, Inc. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.liferay.util;

import com.germinus.easyconf.AggregatedProperties;
import com.germinus.easyconf.ComponentConfiguration;
import com.germinus.easyconf.ComponentProperties;
import com.germinus.easyconf.EasyConf;

import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.model.Company;
import com.liferay.portal.service.CompanyLocalServiceUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.MapConfiguration;

/**
 * <a href="ExtPropertiesLoader.java.html"><b><i>View Source</i></b></a>
 *
 * @author Brian Wing Shun Chan
 *
 */
public class ExtPropertiesLoader {

	public static ExtPropertiesLoader getInstance(
		ClassLoader classLoader, String name) {

		ExtPropertiesLoader props = _propsPool.get(name);

		if (props == null) {
			props = new ExtPropertiesLoader(classLoader, name);

			_propsPool.put(name, props);
		}

		return props;
	}

	public static ExtPropertiesLoader getInstance(
		ClassLoader classLoader, String name, long companyId) {

		String key = name + _COMPANY_ID_SEPARATOR + companyId;

		ExtPropertiesLoader props = _propsPool.get(key);

		if (props == null) {
			props = new ExtPropertiesLoader(classLoader, name, companyId);

			_propsPool.put(key, props);
		}

		return props;
	}

	public void addProperties(Properties properties) {
		ComponentProperties componentProperties = _conf.getProperties();

		AggregatedProperties aggregatedProperties =
			(AggregatedProperties)componentProperties.toConfiguration();

		aggregatedProperties.addConfiguration(new MapConfiguration(properties));
	}

	public boolean containsKey(String key) {
		return getComponentProperties().containsKey(key);
	}

	public String get(String key) {
		if (_PRINT_DUPLICATE_KEYS) {
			if (_keys.contains(key)) {
				System.out.println("Duplicate key " + key);
			}
			else {
				_keys.add(key);
			}
		}

		return getComponentProperties().getString(key);
	}

	public void set(String key, String value) {
		getComponentProperties().setProperty(key, value);
	}

	public String[] getArray(String key) {
		String[] array = getComponentProperties().getStringArray(key);

		if (array == null) {
			return new String[0];
		}
		else if (array.length > 0) {

			// Commons Configuration parses an empty property into a String
			// array with one String containing one space. It also leaves a
			// trailing array member if you set a property in more than one
			// line.

			if (Validator.isNull(array[array.length - 1])) {
				String[] subArray = new String[array.length - 1];

				System.arraycopy(array, 0, subArray, 0, subArray.length);

				array = subArray;
			}
		}

		return array;
	}

	public Properties getProperties() {

		// For some strange reason, componentProperties.getProperties() returns
		// values with spaces after commas. So a property setting of "xyz=1,2,3"
		// actually returns "xyz=1, 2, 3". This can break applications that
		// don't expect that extra space. However, getting the property value
		// directly through componentProperties returns the correct value. This
		// method fixes the weird behavior by returing properties with the
		// correct values.

		Properties props = new Properties();

		ComponentProperties componentProps = getComponentProperties();

		Iterator<Map.Entry<Object, Object>> itr =
			componentProps.getProperties().entrySet().iterator();

		while (itr.hasNext()) {
			Map.Entry<Object, Object> entry = itr.next();

			String key = (String)entry.getKey();
			String value = (String)entry.getValue();

			props.setProperty(key, value);
		}

		return props;
	}

	public ComponentProperties getComponentProperties() {
		return _conf.getProperties();
	}

	public void removeProperties(Properties properties) {
		ComponentProperties componentProperties = _conf.getProperties();

		AggregatedProperties aggregatedProperties =
			(AggregatedProperties)componentProperties.toConfiguration();

		for (int i = 0; i < aggregatedProperties.getNumberOfConfigurations();
				i++) {

			Configuration configuration =
				aggregatedProperties.getConfiguration(i);

			if (!(configuration instanceof MapConfiguration)) {
				return;
			}

			MapConfiguration mapConfiguration = (MapConfiguration)configuration;

			if (mapConfiguration.getMap() == properties) {
				aggregatedProperties.removeConfiguration(configuration);
			}
		}
	}

	private ExtPropertiesLoader(ClassLoader classLoader, String name) {
		_conf = EasyConf.getConfiguration(_getFileName(classLoader, name));

		_printSources();
	}

	private ExtPropertiesLoader(
		ClassLoader classLoader, String name, long companyId) {

		String webId = null;

		if (companyId > 0) {
			try {
				Company company = CompanyLocalServiceUtil.getCompanyById(
					companyId);

				webId = company.getWebId();
			}
			catch (Exception e) {
			}
		}

		_conf = EasyConf.getConfiguration(
			webId, _getFileName(classLoader, name));

		_printSources(companyId, webId);
	}

	private String _getFileName(ClassLoader classLoader, String name) {
		URL url = classLoader.getResource(name + ".properties");

		try {
			name = new URI(url.getPath()).getPath();
		}
		catch (URISyntaxException urise) {
			name = url.getFile();
		}

		int pos = name.lastIndexOf(".properties");

		if (pos != -1) {
			name = name.substring(0, pos);
		}

		return name;
	}

	private void _printSources() {
		_printSources(0, null);
	}

	private void _printSources(long companyId, String webId) {
		List<String> sources = getComponentProperties().getLoadedSources();

		for (int i = sources.size() - 1; i >= 0; i--) {
			String source = sources.get(i);

			String info = "Loading " + source;

			if (companyId > 0) {
				info +=
					" for {companyId=" + companyId + ", webId=" + webId + "}";
			}

			System.out.println(info);
		}
	}

	private static Map<String, ExtPropertiesLoader> _propsPool =
		new ConcurrentHashMap<String, ExtPropertiesLoader>();

	private static final String _COMPANY_ID_SEPARATOR = "_COMPANY_ID_";

	private static final boolean _PRINT_DUPLICATE_KEYS = false;

	private ComponentConfiguration _conf;
	private Set<String> _keys = new HashSet<String>();

}