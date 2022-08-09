/**
 * MonitoringManager.java
 * DataServices
 * Copyright (C) Tigo Honduras
*/
package hn.com.tigo.josm.monitor.jmx;

import static javax.ejb.ConcurrencyManagementType.BEAN;
import hn.com.tigo.josm.common.dto.JOSMEvent;
import hn.com.tigo.josm.common.interfaces.MonitoringManagerLocal;
import hn.com.tigo.josm.common.jmx.DriverMonitoring;
import hn.com.tigo.josm.common.jmx.DriverMonitoringMXBean;
import hn.com.tigo.josm.common.jmx.JOSMMonitoring;
import hn.com.tigo.josm.common.jmx.JOSMMonitoringMXBean;
import hn.com.tigo.josm.common.jmx.event.ConnectionRefusedEvent;
import hn.com.tigo.josm.common.jmx.event.ConnectionRefusedEventType;
import hn.com.tigo.josm.common.jmx.event.CreateDriverEvent;
import hn.com.tigo.josm.common.jmx.event.EndpointEvent;
import hn.com.tigo.josm.common.jmx.event.MXBeanType;
import hn.com.tigo.josm.common.jmx.event.MonitoringEventType;
import hn.com.tigo.josm.common.jmx.event.PerformanceEvent;
import hn.com.tigo.josm.common.jmx.event.UseDriverEvent;

import java.lang.management.ManagementFactory;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.PostConstruct;
import javax.ejb.Asynchronous;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.apache.log4j.Logger;

/**
 * Session Bean implementation class MonitoringManager.
 * This class manages the monitoring beans of JOSM.
 *
 * @author Jose David Martinez Rico <mailto:jdmartinez@stefanini.com />
 * @version 
 * @see 
 * @since 24/02/2015 02:56:52 PM 2015
 */
@Singleton
@Startup
@ConcurrencyManagement(BEAN)
public class MonitoringManager implements MonitoringManagerLocal {
	
	/** Attribute that determine base name for monitoring beans. */
	private final String _base = "hn.tigo.com.josm.{0}:type={1}";
	
	/** Contains the metrics registered. */
	private Map<String, JOSMMonitoringMXBean> _metrics;
	

	/** Contains the lock's for register. */
	private Map<String, Lock> _locks;
	
	/** Attribute that determine platformMBeanServer. */
	private MBeanServer _platformMBeanServer;
	
	/** Attribute that determine LOGGER. */
	private Logger LOGGER = Logger.getLogger(MonitoringManager.class);
	
	/**
	 * Receive event to monitoring with your result info.
	 *
	 * @param event the event
	 */
	@Asynchronous
	public void receiveEvent(final JOSMEvent event) {

		final MonitoringEventType type = event.getType();
		final MXBeanType mxBeanType = event.getMXBeanType();

		final String key = MessageFormat.format(_base, event.getComponent(),
				event.getObjectName());
		Lock lock = getLock(key);

		JOSMMonitoringMXBean bean;

		lock.lock();
		try {
			bean = _metrics.get(key);
			if (bean == null) {
				bean = registerInJMX(key, mxBeanType);
			}

		} finally {
			lock.unlock();
		}
		
		if (type.equals(MonitoringEventType.PERFORMANCE)) {
			final PerformanceEvent performanceEvent = (PerformanceEvent) event;

			if (performanceEvent.isSucess()) {
				bean.incrementInboundMessages();
			} else {
				bean.incrementFailedMessages();
			}
			bean.setLastTransactionTimeMillis(performanceEvent
					.getTransactionInMillis());
		
		} else {
			final DriverMonitoringMXBean driverBean = (DriverMonitoringMXBean)bean;
			
			if (type.equals(MonitoringEventType.CREATE_DRIVER)) {
				final CreateDriverEvent driverEvent = (CreateDriverEvent) event;
				driverBean.setTotalDrivers(driverEvent.getTotalDriver());
			}else if (type.equals(MonitoringEventType.ENDPOINT)) {
				final EndpointEvent driverEvent = (EndpointEvent) event;
				driverBean.setLastEndpointTimeMillis(driverEvent.get_lastEndpointTransactionInMillis());
			}else if (type.equals(MonitoringEventType.USE_DRIVER)) {
				final UseDriverEvent driverEvent = (UseDriverEvent) event;
				driverBean.setIdleDrivers(driverEvent.getIdlesDrivers());
			}else if(type.equals(MonitoringEventType.CONNECTION_REFUSED_DRIVER)){
				final ConnectionRefusedEvent driverEvent = (ConnectionRefusedEvent)event;
				
				if(driverEvent.getErrorType().equals(ConnectionRefusedEventType.DRIVER)){
					driverBean.incrementInsuficientDrivers();
				}else{
					driverBean.incrementConnectionRefused();
				}
				
				
			}
		}

	}
	
	
    
    /**
     * Gets the lock.
     *
     * @param key the key
     * @return the lock
     */
    private synchronized Lock getLock(String key){
    	Lock lock = _locks.get(key);
    	if(lock == null){
    		lock = new ReentrantLock();
    		_locks.put(key, lock);
    	}
    	
    	return lock;
    }
    
    /**
     * Register in jmx. 
     * This method register a bean in the jmx server and in metrics.
     *
     * @param beanName the bean name
     * @param mxBeanType the mx bean type
     * @return the JOSM monitoring mx bean
     */
	private JOSMMonitoringMXBean registerInJMX(final String beanName, final MXBeanType mxBeanType) {
		
		try {
			JOSMMonitoringMXBean bean = null;
			final ObjectName objectName = new ObjectName(beanName);
			
			if(_platformMBeanServer.isRegistered(objectName)){
				_platformMBeanServer.unregisterMBean(objectName);
			}
			
			if (mxBeanType.equals(MXBeanType.BASIC)) {
				bean = new JOSMMonitoring();
				_platformMBeanServer.registerMBean(bean, objectName);
			} else {
				bean = new DriverMonitoring();
				_platformMBeanServer.registerMBean(
						(DriverMonitoringMXBean) bean, objectName);
			}
			_metrics.put(beanName, bean);
			
			return bean;

		} catch (MalformedObjectNameException | InstanceAlreadyExistsException
				| MBeanRegistrationException | NotCompliantMBeanException | InstanceNotFoundException e) {
			LOGGER.error("Problem during registration of Monitoring into JMX:",
					e);
			throw new IllegalStateException(
					"Problem during registration of Monitoring into JMX:" + e,
					e);
		}
		
		

	}

    /**
     * Register in jmx server and initializes the metrics. 
     */
    @PostConstruct
    public void registerInServer(){
    	_metrics = new HashMap<String, JOSMMonitoringMXBean>();
    	_platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
    	_locks = new HashMap<String, Lock>();
    
    }
    
    
    /**
     * Unregister from jmx server.
     */
    public void unregisterFromJMX() {
		try {
			Set<String> keys = _metrics.keySet();
			
			ObjectName objectName;
			for(String key: keys){
				objectName = new ObjectName(key);
				_platformMBeanServer.unregisterMBean(objectName);
			}
			
		} catch (Exception e) {
			throw new IllegalStateException(
					"Problem during unregistration of Monitoring into JMX:" + e, e);
		}
	}
    
    
   
    
    /**
     * Update tps in the monitoring beans. 
     * This is a scheduled method that executes itself every second. 
     */
    @Schedule(second="*", minute="*", hour="*")
    public void updateTPS(){
    	final Collection<JOSMMonitoringMXBean> beans = _metrics.values();
    	for(JOSMMonitoringMXBean bean : beans){
    		bean.calculateTPS();
    	}
    }

	/* (non-Javadoc)
	 * @see hn.com.tigo.josm.common.interfaces.MonitoringManagerLocal#getTPS(java.lang.String, java.lang.String)
	 */
	@Override
	public long getTPS(final String component, final String objectName) {
		
		final String key = MessageFormat.format(_base,   component, objectName);
		final JOSMMonitoringMXBean bean = _metrics.get(key);
		if(bean == null){
			return 0;
		}
		
		
		return bean.getTPS();
		
	}



	/* (non-Javadoc)
	 * @see hn.com.tigo.josm.common.interfaces.MonitoringManagerLocal#unregisterFromJMX(java.lang.String, java.lang.String)
	 */
	@Asynchronous
	@Override
	public void unregisterFromJMX(final String component, final String objectName) {
		final String key = MessageFormat.format(_base,   component, objectName);
		
		try {
			ObjectName mbean = new ObjectName(key);
			_platformMBeanServer.unregisterMBean(mbean);
			_metrics.remove(key);
		} catch (Exception e) {
			throw new IllegalStateException(
					"Problem during unregistration of Monitoring into JMX:" + e, e);
		}
		
	}
	
	
	
	

}
