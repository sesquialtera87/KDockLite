module KDockLite {
    exports org.mth.docking;

    requires com.formdev.flatlaf;
    requires com.formdev.flatlaf.extras;

    requires java.desktop;
    requires java.logging;

    requires kotlin.stdlib;

    opens org.mth.docking to com.formdev.flatlaf, com.formdev.flatlaf.extras, java.desktop;
}