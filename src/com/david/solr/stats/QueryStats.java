package com.david.solr.stats;

import java.util.Map;



import java.util.logging.Logger;

import com.david.solr.SolrHelper;
import com.david.solr.SolrRemote;
import com.fasterxml.jackson.databind.JsonNode;

public class QueryStats {


	private Double requests;
	private Double errors;
	private Double timeouts;
	private Double avgRequests;
	private Double avgTimePerRequest;
	private Double fiveMinRateRequests;
	
	private static final Logger log = Logger.getLogger(SolrRemote.class.getName());

	public void populateStats(Map<String, JsonNode> solrMBeansHandlersMap, String handler) {

		JsonNode node = solrMBeansHandlersMap.get("QUERYHANDLER");
		
		//log.severe("UPDATES: " + node.path("/update"));
		//log.severe("1 - SELECT: " + node.path("/select").path("stats"));
		//log.severe("2 - SELECT: " + node.path(handler).path("stats"));
		//log.severe("/select : " + handler );
		
		//log.severe("NODE TEM: " + node.size() + " elementos");

		//log.warning("PATH QUERY " + node);
		if (node != null) {
			JsonNode searchStats = node.path(handler).path("stats");
			if (!searchStats.isMissingNode()) {
				this.setRequests(searchStats.path("requests").asDouble());
				this.setErrors(searchStats.path("errors").asDouble());
				this.setTimeouts(searchStats.path("timeouts").asDouble());
				this.setAvgRequests(searchStats.path("avgRequestsPerSecond").asDouble());
				this.setFiveMinRateRequests(SolrHelper.multipyBy(searchStats.path("5minRateReqsPerSecond").asDouble(), 60));
				this.setAvgTimePerRequest(searchStats.path("avgTimePerRequest").asDouble());
			} else {

				log.warning("Missing Handler " + handler + " in this Solr");
			}
		}
	}

	public Double getRequests() {
		return requests;
	}

	public void setRequests(Double requests) {
		this.requests = requests;
	}

	public Double getErrors() {
		return errors;
	}

	public void setErrors(Double errors) {
		this.errors = errors;
	}

	public Double getTimeouts() {
		return timeouts;
	}

	public void setTimeouts(Double timeouts) {
		this.timeouts = timeouts;
	}

	public Double getAvgRequests() {
		return avgRequests;
	}

	public void setAvgRequests(Double avgRequests) {
		this.avgRequests = avgRequests;
	}

	public Double getAvgTimePerRequest() {
		return avgTimePerRequest;
	}

	public void setAvgTimePerRequest(Double avgTimePerRequest) {
		this.avgTimePerRequest = avgTimePerRequest;
	}

	public Double getFiveMinRateRequests() {
		return fiveMinRateRequests;
	}

	public void setFiveMinRateRequests(Double fiveMinRateRequests) {
		this.fiveMinRateRequests = fiveMinRateRequests;
	}
}
