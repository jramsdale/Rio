import org.rioproject.config.Constants

/*
 * Configuration properties used to launch Rio services from the test framework
 */
manager {
    
    execClassPath =
        '${RIO_HOME}${/}lib${/}boot.jar${:}${RIO_HOME}${/}lib/${/}start.jar${:}${JAVA_HOME}${/}lib${/}tools.jar${:}${RIO_HOME}${/}lib${/}groovy-all.jar'

    inheritOptions = true

    jvmOptions='''
        -server -Xms8m -Xmx256m -Djava.security.policy=${RIO_HOME}${/}policy${/}policy.all
        -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${RIO_HOME}${/}logs
        -DRIO_HOME=${RIO_HOME} -DRIO_TEST_HOME=${RIO_TEST_HOME}
        -Dorg.rioproject.home=${RIO_HOME} -Dorg.rioproject.groups=${org.rioproject.groups}'''

    /* The ${service} token will be replaced by the name of the starter file.
     * For start-reggie the service name will be reggie, for start-monitor the
     * service name will be monitor, etc ... */
    String logDir = '/tmp/logs'
    String opSys = System.getProperty('os.name')
    if(opSys.startsWith("Windows"))
        logDir = '${java.io.tmpdir}'
    
    String logExt = System.getProperty(Constants.GROUPS_PROPERTY_NAME,
                                       System.getProperty('user.name'))
    log = logDir+'${/}rio${/}'+logExt+'${/}${service}.log'

    /*
     * Remove any previously created service log files
     */
    cleanLogs = true

    mainClass='com.sun.jini.start.ServiceStarter'

    reggieStarter = '${RIO_TEST_HOME}${/}src${/}test${/}conf${/}start-reggie.groovy'

    monitorStarter = '${RIO_TEST_HOME}${/}src${/}test${/}conf${/}start-monitor.groovy'

    cybernodeStarter = '${RIO_HOME}${/}config${/}start-cybernode.groovy'

    //config = '${RIO_HOME}${/}config${/}tools.groovy'

    harvesterOpString = '${RIO_TEST_HOME}${/}src${/}test${/}resources${/}harvester.groovy'

    harvestDir = '${RIO_TEST_HOME}${/}target${/}failsafe-reports${/}logs'

}

