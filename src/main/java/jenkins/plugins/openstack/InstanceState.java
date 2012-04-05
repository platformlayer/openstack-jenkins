//package jenkins.plugins.openstack;
//
///**
// * Constants that represent the running state of an OpenStack instance. 
// *
// * @author Kohsuke Kawaguchi
// * @author Justin SB
// */
//public enum InstanceState {
//    BUILD,
//    ACTIVE;
//    
////    PENDING,
////    RUNNING,
////    SHUTTING_DOWN,
////    TERMINATED,
////    STOPPING,
////    STOPPED;
//
//    public String getCode() {
//        return name().toLowerCase().replace('_','-');
//    }
//
//    public static InstanceState find(String name) {
//        return Enum.valueOf(InstanceState.class,name.toUpperCase().replace('-','_'));
//    }
//}
