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

package com.liferay.util.dao.hibernate;

import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.util.ExtPropertiesLoader;

import java.io.InputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.hibernate.cfg.Configuration;

import org.springframework.orm.hibernate3.LocalSessionFactoryBean;

/**
 * <a href="PropsHibernateConfiguration.java.html"><b><i>View Source</i></b></a>
 *
 * @author Brian Wing Shun Chan
 *
 */
public class PropsHibernateConfiguration extends LocalSessionFactoryBean {

	public void setPropsName(String propsName) {
		_propsName = propsName;
	}

	protected Configuration newConfiguration() {
		Configuration configuration = new Configuration();

		try {
			ExtPropertiesLoader extPropsLoader =
				ExtPropertiesLoader.getInstance(
					getClass().getClassLoader(), _propsName);

			ClassLoader classLoader = getClass().getClassLoader();

			String[] configs = StringUtil.split(
				extPropsLoader.get("hibernate.configs"));

			for (int i = 0; i < configs.length; i++) {
				try {
					InputStream is =
						classLoader.getResourceAsStream(configs[i]);

					if (is != null) {
						configuration = configuration.addInputStream(is);

						is.close();
					}
				}
				catch (Exception e) {
					_log.error(e.getMessage());
				}
			}

			configuration.setProperties(extPropsLoader.getProperties());
		}
		catch (Exception e) {
			_log.error(e, e);
		}

		return configuration;
	}

	private static Log _log =
		LogFactory.getLog(PropsHibernateConfiguration.class);

	private String _propsName;

}