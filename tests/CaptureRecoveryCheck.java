import com.local.paralleleyeconverter.CaptureRecovery;

/** Standalone JVM regressions; does not emulate MediaProjection or use a device. */
public final class CaptureRecoveryCheck {
    private static int checks;

    private static void expect(String label, boolean actual, boolean expected) {
        checks++;
        if (actual != expected) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        CaptureRecovery recovery = new CaptureRecovery();
        expect("inactive session", recovery.claimRecovery(100000), false);
        recovery.start(10000);
        expect("no first frame before timeout", recovery.claimRecovery(12199), false);
        expect("no first frame at timeout", recovery.claimRecovery(12200), true);
        expect("failed recovery is rate limited", recovery.claimRecovery(13000), false);
        expect("retry before cooldown", recovery.claimRecovery(15699), false);
        expect("retry at cooldown", recovery.claimRecovery(15700), true);
        recovery.onFrame(18000);
        expect("fresh frame", recovery.claimRecovery(18000), false);
        expect("frame before timeout", recovery.claimRecovery(20199), false);
        expect("stream stalls after a frame", recovery.claimRecovery(20200), true);

        recovery.setVisible(false, 20500);
        expect("hidden target never retries", recovery.claimRecovery(999999), false);
        recovery.setVisible(true, 1000000);
        expect("returning target gets a grace period", recovery.claimRecovery(1002199), false);
        expect("returning target can recover", recovery.claimRecovery(1002200), true);
        recovery.stop();
        recovery.onFrame(2000000);
        recovery.setVisible(true, 2000000);
        expect("stopped consent cannot recover", recovery.claimRecovery(3000000), false);

        recovery.start(0);
        expect("clock origin is a valid session start", recovery.claimRecovery(2200), true);
        recovery.stop();
        recovery.start(0);
        recovery.setVisible(true, 2000);
        expect("duplicate visibility callback does not postpone recovery", recovery.claimRecovery(2200), true);

        recovery.start(0);
        recovery.onRecoveryAttempt(1000);
        expect("explicit surface rebind starts cooldown", recovery.claimRecovery(2200), false);
        expect("explicit rebind cooldown boundary", recovery.claimRecovery(4500), true);

        recovery.setVisible(false, 5000);
        recovery.stop();
        recovery.start(10000);
        expect("new consent resets hidden/stopped state", recovery.claimRecovery(12200), true);

        recovery.start(0);
        for (int i = 1; i <= 100; i++) {
            long now = i * 1800L;
            recovery.onFrame(now);
            expect("continuous frame stream " + i, recovery.claimRecovery(now + 1799), false);
        }
        expect("recovery resumes after real stream stalls", recovery.claimRecovery(182200), true);

        expect("healthy same-size display stays intact",
            CaptureRecovery.Companion.needsSurfaceReplacement(false, true, true, true), false);
        expect("same-size STALL must replace the surface",
            CaptureRecovery.Companion.needsSurfaceReplacement(true, true, true, true), true);
        expect("resize replaces surface",
            CaptureRecovery.Companion.needsSurfaceReplacement(false, false, true, true), true);
        expect("missing ImageReader replaces surface",
            CaptureRecovery.Companion.needsSurfaceReplacement(false, true, true, false), true);
        expect("missing initial display needs setup",
            CaptureRecovery.Companion.needsSurfaceReplacement(false, true, false, true), true);

        System.out.println("PASS: " + checks + " capture recovery assertions");
    }
}
