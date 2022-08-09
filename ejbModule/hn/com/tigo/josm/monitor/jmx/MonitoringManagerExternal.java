package hn.com.tigo.josm.monitor.jmx;

import hn.com.tigo.josm.common.dto.JOSMEvent;
import hn.com.tigo.josm.common.interfaces.MonitoringManagerLocal;
import hn.com.tigo.josm.common.interfaces.MonitoringManagerRemote;

import javax.ejb.EJB;
import javax.ejb.Stateless;

/**
 * Session Bean implementation class MonitoringManagerExternal.
 * This class manages the external monitoring request.
 *
 * @author Jose David Martinez Rico <mailto:jdmartinez@stefanini.com />
 * @version 
 * @see 
 * @since 24/02/2015 02:56:52 PM 2015
 */
@Stateless
public class MonitoringManagerExternal implements MonitoringManagerRemote {
	
	/** Attribute that determine monitoringManager. */
	@EJB
	private MonitoringManagerLocal monitoringManager;

    /**
     * Default constructor. 
     */
    public MonitoringManagerExternal() {
    }

	/* (non-Javadoc)
	 * @see hn.com.tigo.josm.common.interfaces.MonitoringManagerRemote#receiveEvent(hn.com.tigo.josm.common.dto.JOSMEvent)
	 */
	@Override
	public void receiveEvent(JOSMEvent event) {
		monitoringManager.receiveEvent(event);
	}
	

	/* (non-Javadoc)
	 * @see hn.com.tigo.josm.common.interfaces.MonitoringManagerRemote#getTPS(java.lang.String, java.lang.String)
	 */
	@Override
	public long getTPS(String component, String objectName) {
		return monitoringManager.getTPS(component, objectName);
	}

	/* (non-Javadoc)
	 * @see hn.com.tigo.josm.common.interfaces.MonitoringManagerRemote#unregisterFromJMX(java.lang.String, java.lang.String)
	 */
	@Override
	public void unregisterFromJMX(final String component, final String objectName) {
		monitoringManager.unregisterFromJMX(component, objectName);
		
	}
	
	
    
}
