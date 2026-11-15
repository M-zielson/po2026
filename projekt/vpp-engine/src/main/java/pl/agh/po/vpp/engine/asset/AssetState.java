package pl.agh.po.vpp.engine.asset;

/**
 * Stan operacyjny zasobu w silniku symulacji (wzorzec State).
 */
public enum AssetState {
    RUNNING,
    FAILED,
    DISABLED
}