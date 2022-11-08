package net.william278.husktowns.hook.luckperms;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.ContextManager;
import net.william278.husktowns.HuskTowns;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class LuckPermsHook {

    private ContextManager contextManager;
    private final List<ContextCalculator<Player>> registeredCalculators = new ArrayList<>();
    private static final HuskTowns plugin = HuskTowns.getInstance();

    public LuckPermsHook() {
        LuckPerms luckPerms = plugin.getServer().getServicesManager().load(LuckPerms.class);
        if (luckPerms == null) {
            plugin.getLogger().warning("Failed to load the LuckPermsAPI despite being installed; is LuckPerms up-to-date?");
            return;
        }
        this.contextManager = luckPerms.getContextManager();
        registerProviders();
        plugin.getLogger().info("Enabled HuskTowns LuckPerms context provider hook!");
    }

    private void registerProviders() {
        registerProvider(ClaimCalculator::new);
        registerProvider(PlayerTownCalculator::new);
        registerProvider(PlayerAccessCalculator::new);
    }

    private void registerProvider(Supplier<ContextCalculator<Player>> calculatorSupplier) {
        ContextCalculator<Player> contextCalculator = calculatorSupplier.get();
        this.contextManager.registerCalculator(contextCalculator);
        this.registeredCalculators.add(contextCalculator);
    }

    public void unRegisterProviders() {
        this.registeredCalculators.forEach(contextCalculator -> this.contextManager.unregisterCalculator(contextCalculator));
        this.registeredCalculators.clear();
    }

    // Asynchronously convert to chunk coordinate (without loading chunk)
    public static int toChunkCoordinate(double value) {
        return floor(value) >> 4;
    }

    private static int floor(double num) {
        int floor = (int) num;
        return floor == num ? floor : floor - (int) (Double.doubleToRawLongBits(num) >>> 63);
    }

}
