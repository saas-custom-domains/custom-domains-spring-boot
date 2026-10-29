package com.example.billport;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class Hosts {

	private static final Pattern PORT = Pattern.compile(":\\d+$");

	private static final Pattern LABEL = Pattern.compile("^(?!-)[a-z0-9-]{1,63}(?<!-)$");

	private final List<String> appHosts;

	private final String appDomain;

	public Hosts(AppProperties props) {
		this.appHosts = props.hosts().stream().map(Hosts::normalize).filter(h -> !h.isEmpty()).toList();
		this.appDomain = normalize(props.domain());
	}

	/** "Billing.Acme.com." and "billing.acme.com:443" are the same host. */
	public static String normalize(String input) {
		if (input == null) {
			return "";
		}
		String host = PORT.matcher(input.trim().toLowerCase(Locale.ROOT)).replaceFirst("");
		return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
	}

	public static boolean isValid(String host) {
		if (host.length() > 253) {
			return false;
		}
		String[] labels = host.split("\\.", -1);
		if (labels.length < 2) {
			return false;
		}
		for (String label : labels) {
			if (!LABEL.matcher(label).matches()) {
				return false;
			}
		}
		return true;
	}

	public boolean isAppHost(String host) {
		return appHosts.contains(host);
	}

	/** Our own hostnames, our domain and its subdomains. */
	public boolean isReserved(String host) {
		if (isAppHost(host)) {
			return true;
		}
		return !appDomain.isEmpty() && (host.equals(appDomain) || host.endsWith("." + appDomain));
	}

}
