
 /**
  * This template file was generated by dynaTrace client.
  * The dynaTrace community portal can be found here: http://community.compuwareapm.com/
  * For information how to publish a plugin please visit http://community.compuwareapm.com/plugins/contribute/
  **/ 

package com.david.solr;

import com.david.solr.config.Configuration;
import com.david.solr.config.Core;
import com.david.solr.config.Server;
import com.david.solr.stats.CacheStats;
import com.david.solr.stats.CoreStats;
import com.david.solr.stats.MemoryStats;
import com.david.solr.stats.QueryStats;
import com.dynatrace.diagnostics.pdk.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;


public class SolrRemote implements Monitor {
	
	private String context_root;
	//private static final String CORE_URI = "/admin/cores?action=STATUS&wt=json";
	private static String plugins_uri = "/%s/admin/plugins?wt=json";
	private static String memory_uri = "/%s/admin/system?stats=true&wt=json";
	private static String mbeansUri = "/%s/admin/mbeans?stats=true&wt=json";
	
	CloseableHttpClient httpclient;
	SolrHelper helper;

	private static final Logger log = Logger.getLogger(SolrRemote.class.getName());


	/**
	 * Initializes the Plugin. This method is called in the following cases:
	 * <ul>
	 * <li>before <tt>execute</tt> is called the first time for this
	 * scheduled Plugin</li>
	 * <li>before the next <tt>execute</tt> if <tt>teardown</tt> was called
	 * after the last execution</li>
	 * </ul>
	 * <p>
	 * If the returned status is <tt>null</tt> or the status code is a
	 * non-success code then {@link Plugin#teardown() teardown()} will be called
	 * next.
	 * <p>
	 * Resources like sockets or files can be opened in this method.
	 * @param env
	 *            the configured <tt>MonitorEnvironment</tt> for this Plugin;
	 *            contains subscribed measures, but <b>measurements will be
	 *            discarded</b>
	 * @see Plugin#teardown()
	 * @return a <tt>Status</tt> object that describes the result of the
	 *         method call
	 */
	@Override
	public Status setup(MonitorEnvironment env) throws Exception {

		return new Status(Status.StatusCode.Success);
	}

	/**
	 * Executes the Monitor Plugin to retrieve subscribed measures and store
	 * measurements.
	 *
	 * <p>
	 * This method is called at the scheduled intervals. If the Plugin execution
	 * takes longer than the schedule interval, subsequent calls to
	 * {@link #execute(MonitorEnvironment)} will be skipped until this method
	 * returns. After the execution duration exceeds the schedule timeout,
	 * {@link TaskEnvironment#isStopped()} will return <tt>true</tt>. In this
	 * case execution should be stopped as soon as possible. If the Plugin
	 * ignores {@link TaskEnvironment#isStopped()} or fails to stop execution in
	 * a reasonable timeframe, the execution thread will be stopped ungracefully
	 * which might lead to resource leaks!
	 *
	 * @param env
	 *            a <tt>MonitorEnvironment</tt> object that contains the
	 *            Plugin configuration and subscribed measures. These
	*            <tt>MonitorMeasure</tt>s can be used to store measurements.
	 * @return a <tt>Status</tt> object that describes the result of the
	 *         method call
	 */
	@Override
	public Status execute(MonitorEnvironment env) throws Exception {
		/* 
		// this sample which shows how to book to (dynamic) monitor measures

		Collection<MonitorMeasure> monitorMeasures = env.getMonitorMeasures("mymetricgroup", "mymetric");
		for (MonitorMeasure subscribedMonitorMeasure : monitorMeasures) {

			//this will book to the monitor measure
			subscribedMonitorMeasure.setValue(42);

			//for this subscribed measure we want to create a dynamic measure
			MonitorMeasure dynamicMeasure = env.createDynamicMeasure(subscribedMonitorMeasure, "Queue Name", "Queue 1");
			dynamicMeasure.setValue(24);

			//now we create another one for a different queue name
			dynamicMeasure = env.createDynamicMeasure(subscribedMonitorMeasure, "Queue Name", "Queue 2");
			dynamicMeasure.setValue(32);


		}
		*/
		String host = env.getHost().getAddress();
		int port = Integer.parseInt(env.getConfigString("port"));
		List<String> queryHandlers = Arrays.asList(env.getConfigString("handlers").split(";"));

		List<String> cores = Arrays.asList(env.getConfigString("cores").split(";"));
		List<Core> listaCores = new ArrayList<Core>();
		
		context_root = "http://" + host + ":" + port + "/solr";		

		for (String nomeCore : cores) {
			Core core = new Core();
			core.setName(nomeCore);
			core.setQueryHandlers(queryHandlers);
			listaCores.add(core);
		}
		
		httpclient = HttpClients.createDefault();
		
		
		//http://10.128.132.138:8080/solr/Produto/admin/mbeans?stats=true
		Server server = new Server();
		server.setHost(host);
		server.setPort(port);

		Configuration config = new Configuration();
		config.setCores(listaCores);
		config.setServer(server);
		
		helper = new SolrHelper(httpclient);

		log.warning("H� " + cores.size() + " cores");
		for (Core core : listaCores) {
			log.warning("Core " + (listaCores.indexOf(core) + 1) + ": " +  core.getName());

		}
		
		try{
			getSOLRMetrics(httpclient, helper, listaCores, env);
		}catch(Exception e){
			log.severe("ERRO: "+ e.getMessage());
		}
			
		return new Status(Status.StatusCode.Success);
	}

	/**
	 * Shuts the Plugin down and frees resources. This method is called in the
	 * following cases:
	 * <ul>
	 * <li>the <tt>setup</tt> method failed</li>
	 * <li>the Plugin configuration has changed</li>
	 * <li>the execution duration of the Plugin exceeded the schedule timeout</li>
	 * <li>the schedule associated with this Plugin was removed</li>
	 * </ul>
	 *
	 * <p>
	 * The Plugin methods <tt>setup</tt>, <tt>execute</tt> and
	 * <tt>teardown</tt> are called on different threads, but they are called
	 * sequentially. This means that the execution of these methods does not
	 * overlap, they are executed one after the other.
	 *
	 * <p>
	 * Examples:
	 * <ul>
	 * <li><tt>setup</tt> (failed) -&gt; <tt>teardown</tt></li>
	 * <li><tt>execute</tt> starts, configuration changes, <tt>execute</tt>
	 * ends -&gt; <tt>teardown</tt><br>
	 * on next schedule interval: <tt>setup</tt> -&gt; <tt>execute</tt> ...</li>
	 * <li><tt>execute</tt> starts, execution duration timeout,
	 * <tt>execute</tt> stops -&gt; <tt>teardown</tt></li>
	 * <li><tt>execute</tt> starts, <tt>execute</tt> ends, schedule is
	 * removed -&gt; <tt>teardown</tt></li>
	 * </ul>
	 * Failed means that either an unhandled exception is thrown or the status
	 * returned by the method contains a non-success code.
	 *
	 *
	 * <p>
	 * All by the Plugin allocated resources should be freed in this method.
	 * Examples are opened sockets or files.
	 *
	 * @see Monitor#setup(MonitorEnvironment)
	 */	@Override
	public void teardown(MonitorEnvironment env) throws Exception {
		 
		 if (httpclient != null){
			 httpclient.close();
		 }
		 if (helper != null){
			 helper = null;
		 }

	}
	 
	 public List<Core> getCores(SolrHelper helper, Configuration config) {
		 
			List<Core> cores = new ArrayList<Core>();
			if (config != null && config.getCores() != null) {
				cores = config.getCores();
			}
			
			Iterator<Core> iterator = cores.iterator();
			while (iterator.hasNext()) {
				if (Strings.isNullOrEmpty(iterator.next().getName())) {
					iterator.remove();
				}
			}

			return cores;
		}
		private void getSOLRMetrics(CloseableHttpClient httpClient,
				SolrHelper helper,
				List<Core> coresConfig,
				MonitorEnvironment env) throws IOException {
			
			

			for (Core coreConfig : coresConfig) {
				String core = coreConfig.getName();
				
				if (helper.checkIfMBeanHandlerSupported(String.format(context_root + plugins_uri, core))) {
					Map<String, JsonNode> solrMBeansHandlersMap = new HashMap<String, JsonNode>();
					try {
						solrMBeansHandlersMap = helper.getSolrMBeansHandlersMap(core, context_root + mbeansUri);
					} catch (Exception e) {
						System.out.println("Error in retrieving mbeans info for " + core);
						log.severe("Error in retrieving mbeans info for " + core);
						log.severe(e.getMessage());
						 
						
					}

					try {
						CoreStats coreStats = new CoreStats();
						coreStats.populateStats(solrMBeansHandlersMap);
						
						Iterables.get(env.getMonitorMeasures("Core Stats", "Num Docs"), 0).setValue(coreStats.getNumDocs());
						Iterables.get(env.getMonitorMeasures("Core Stats", "Max Docs"), 0).setValue(coreStats.getMaxDocs());
						Iterables.get(env.getMonitorMeasures("Core Stats", "Deleted Docs"), 0).setValue(coreStats.getDeletedDocs());
	
						System.out.println(core + " " + coreStats.getNumDocs());
						//log.warning("CoreStats: " + core + " " + coreStats.getNumDocs());
					} catch (Exception e) {
						System.out.println("Error Retrieving Core Stats for " + core);
						log.severe("Error Retrieving Core Stats for " + core);
						log.severe(e.getMessage());
						 
					}

					try {
						Double somaRequests = 0.0;
						Double somaErrors = 0.0;
						Double somaTimeouts = 0.0;
						Double somaAvgRequests = 0.0;
						Double somaAvgTimePerRequest = 0.0;
						Double countAvgTimePerRequest = 0.0;
						Double somaFiveMinRateRequests = 0.0;
						
						for (String handler : coreConfig.getQueryHandlers()) {
							QueryStats queryStats = new QueryStats();
							queryStats.populateStats(solrMBeansHandlersMap, handler);

							
							//TODO VALIDAR ESSA PORRA
							somaRequests += queryStats.getRequests();
							Iterables.get(env.getMonitorMeasures("Query Stats", "Requests"), 0).setValue(somaRequests);
							env.createDynamicMeasure(Iterables.get(
									env.getMonitorMeasures(
											"Query Stats", "Requests"), 0),
											"Handler", handler).setValue(
											queryStats.getRequests());
							
							somaErrors  += queryStats.getErrors();
							Iterables.get(env.getMonitorMeasures("Query Stats", "Errors"), 0).setValue(somaErrors);
							env.createDynamicMeasure(Iterables.get(
									env.getMonitorMeasures(
											"Query Stats", "Errors"), 0),
											"Handler", handler).setValue(
											queryStats.getErrors());
							
							somaTimeouts += queryStats.getTimeouts();
							Iterables.get(env.getMonitorMeasures("Query Stats", "Timeouts"), 0).setValue(somaTimeouts);
							env.createDynamicMeasure(Iterables.get(
									env.getMonitorMeasures(
											"Query Stats", "Timeouts"), 0),
											"Handler", handler).setValue(
											queryStats.getTimeouts());
							
							somaAvgRequests += queryStats.getAvgRequests();
							Iterables.get(env.getMonitorMeasures("Query Stats", "Avg Requests"), 0).setValue(somaAvgRequests);
							env.createDynamicMeasure(Iterables.get(
									env.getMonitorMeasures(
											"Query Stats", "Avg Requests"), 0),
											"Handler", handler).setValue(
											queryStats.getAvgRequests());
							
							countAvgTimePerRequest += 1.0;
							somaAvgTimePerRequest += queryStats.getAvgTimePerRequest();
							Iterables.get(env.getMonitorMeasures("Query Stats", "Avg Time Per Request"), 0).setValue(somaAvgTimePerRequest / countAvgTimePerRequest);
							env.createDynamicMeasure(Iterables.get(
									env.getMonitorMeasures(
											"Query Stats", "Avg Time Per Request"), 0),
											"Handler", handler).setValue(
											queryStats.getAvgTimePerRequest());
							
							somaFiveMinRateRequests += queryStats.getFiveMinRateRequests();
							Iterables.get(env.getMonitorMeasures("Query Stats", "Five Min Rate Requests"), 0).setValue(somaFiveMinRateRequests);
							env.createDynamicMeasure(Iterables.get(
									env.getMonitorMeasures(
											"Query Stats", "Five Min Rate Requests"), 0),
											"Handler", handler).setValue(
											queryStats.getFiveMinRateRequests());
							
						}

					} catch (Exception e) {
						System.out.println("Error Retrieving Query Stats for " + core);
						log.severe("Error Retrieving Query Stats for " + core);

						log.severe(e.getMessage());
					}

					try {
						CacheStats cacheStats = new CacheStats();
						cacheStats.populateStats(solrMBeansHandlersMap);
						System.out.println("Cache: " + core + " " + cacheStats.getDocumentCacheHitRatio());
						
						Iterables.get(env.getMonitorMeasures("Cache Stats", "Query Result Cache Hit Ratio"), 0).setValue(cacheStats.getQueryResultCacheHitRatio());
						Iterables.get(env.getMonitorMeasures("Cache Stats", "Query Result Cache Hit Ratio Cumulative"), 0).setValue(cacheStats.getQueryResultCacheHitRatioCumulative());
						Iterables.get(env.getMonitorMeasures("Cache Stats", "Query Result Cache Size"), 0).setValue(cacheStats.getQueryResultCacheSize());
						Iterables.get(env.getMonitorMeasures("Cache Stats", "Document Cache Hit Ratio"), 0).setValue(cacheStats.getDocumentCacheHitRatio());
						Iterables.get(env.getMonitorMeasures("Cache Stats", "Document Cache Hit Ratio Cumulative"), 0).setValue(cacheStats.getDocumentCacheHitRatioCumulative());
						Iterables.get(env.getMonitorMeasures("Cache Stats", "Document Cache Size"), 0).setValue(cacheStats.getDocumentCacheSize());
						Iterables.get(env.getMonitorMeasures("Cache Stats", "Field Value Cache Hit Ratio"), 0).setValue(cacheStats.getFieldValueCacheHitRatio());
						Iterables.get(env.getMonitorMeasures("Cache Stats", "Field Value Cache Hit Ratio Cumulative"), 0).setValue(cacheStats.getFieldValueCacheHitRatioCumulative());
						Iterables.get(env.getMonitorMeasures("Cache Stats", "Field Value Cache Size"), 0).setValue(cacheStats.getFieldValueCacheSize());
						Iterables.get(env.getMonitorMeasures("Cache Stats", "Filter Cache Hit Ratio"), 0).setValue(cacheStats.getFilterCacheHitRatio());
						Iterables.get(env.getMonitorMeasures("Cache Stats", "Filter Cache Hit Ratio Cumulative"), 0).setValue(cacheStats.getFilterCacheHitRatioCumulative());
						Iterables.get(env.getMonitorMeasures("Cache Stats", "Filter Cache Size"), 0).setValue(cacheStats.getFilterCacheSize());
						
						//log.warning("Cache: " + core + " " + cacheStats.getDocumentCacheHitRatio());
					} catch (Exception e) {
						System.out.println("Error Retrieving Cache Stats for " + core);
						log.severe("Error Retrieving Cache Stats for " + core);
						log.severe(e.getMessage());

					}
				}
				try {
					MemoryStats memoryStats = new MemoryStats();
					String uri = context_root + String.format(memory_uri, core);
					//log.warning(uri);
					
					HttpGet request = new HttpGet(uri);
					HttpResponse response = httpClient.execute(request);
					InputStream inputStream = response.getEntity().getContent();
					
					memoryStats.populateStats(inputStream);
					
					//log.warning("WARNING PORRA: " + memoryStats.getJvmMemoryUsed());

					Iterables.get(env.getMonitorMeasures("Memory Stats", "JVM Memory Used"), 0).setValue(memoryStats.getJvmMemoryUsed());
					Iterables.get(env.getMonitorMeasures("Memory Stats", "JVM Memory Free"), 0).setValue(memoryStats.getJvmMemoryFree());
					Iterables.get(env.getMonitorMeasures("Memory Stats", "JVM Memory Total"), 0).setValue(memoryStats.getJvmMemoryTotal());
					Iterables.get(env.getMonitorMeasures("Memory Stats", "Free Physical Memory Size"), 0).setValue(memoryStats.getFreePhysicalMemorySize());
					Iterables.get(env.getMonitorMeasures("Memory Stats", "Total Physical Memory Size"), 0).setValue(memoryStats.getTotalPhysicalMemorySize());
					Iterables.get(env.getMonitorMeasures("Memory Stats", "Committed Virtual Memory Size"), 0).setValue(memoryStats.getCommittedVirtualMemorySize());
					Iterables.get(env.getMonitorMeasures("Memory Stats", "Free Swap Space Size"), 0).setValue(memoryStats.getFreeSwapSpaceSize());
					Iterables.get(env.getMonitorMeasures("Memory Stats", "Total Swap Space Size"), 0).setValue(memoryStats.getTotalSwapSpaceSize());
					Iterables.get(env.getMonitorMeasures("Memory Stats", "Open File Descriptor Count"), 0).setValue(memoryStats.getOpenFileDescriptorCount());
					Iterables.get(env.getMonitorMeasures("Memory Stats", "Max File Descriptor Count"), 0).setValue(memoryStats.getMaxFileDescriptorCount());
					
					
				} catch (Exception e) {
					System.out.println("Error retrieving memory stats for " + core);
					log.severe("Error retrieving memory stats for " + core);
					log.severe(e.getMessage());
					StackTraceElement[] sts = e.getStackTrace();
					for (StackTraceElement stackTraceElement : sts) {
						log.severe(stackTraceElement.toString());
					}
					e.printStackTrace();
				}
			}
		}
}