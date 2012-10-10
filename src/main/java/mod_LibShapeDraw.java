import java.util.List;
import java.util.Map;

import libshapedraw.MinecraftAccess;
import libshapedraw.ApiInfo;
import libshapedraw.internal.Controller;
import libshapedraw.internal.GlobalSettings;
import libshapedraw.primitive.ReadonlyVector3;
import libshapedraw.primitive.Vector3;
import net.minecraft.client.Minecraft;

/**
 * Internal class. Client code using the API should ignore this.
 * Rather, instantiate LibShapeDraw.
 * <p>
 * This is a ModLoader mod that links itself to the internal API Controller,
 * providing it data and events from Minecraft. Basically a bootstrapper and
 * an API bridge.
 * <p>
 * As an API bridge, all direct interaction with Minecraft objects passes
 * through this class, making the API itself clean and free of obfuscated
 * code. (There is a single exception: ModDirectory.DIRECTORY.)
 */
public class mod_LibShapeDraw extends BaseMod implements MinecraftAccess {
    /**
     * Define a dummy Entity subclass to assist with hooking into
     * Minecraft's rendering system. By spawning a local, non-tracked,
     * "ghost" Entity for each world, we're able to render shapes in the game
     * world itself rather than on top of everything (including the HUD and
     * GUI windows).
     * <p>
     * This ghost entity is trimmed down to be as lightweight as possible.
     * It neither interacts with the environment nor is rendered directly so
     * we can remove a lot.
     * <p>
     * Later on, we define an associated ghost entity renderer and register
     * it with ModLoader. This dummy renderer simply passes on the render
     * event to the Controller, where the real work happens.
     * <p>
     * This is a little convoluted but it has the key advantage of NOT
     * requiring the modification of any vanilla classes beyond what ModLoader
     * has already done.
     * <p>
     * This technique is used by several mods including WorldEditCUI (authors:
     * lahwran and yetanotherx).
     * @see https://github.com/yetanotherx/WorldEditCUI
     */
    // obf: Entity
    public class GhostEntity extends jn {
        // obf: World
        public GhostEntity(up world) {
            super(world);
            // Disable frustum check; we always want to render this entity.
            this.ak = true; // obf: Entity.ignoreFrustumCheck
            setPositionToPlayer();
        }
        /**
         * Set the ghost entity to the player's location. It isn't necessary
         * for the locations to match exactly, as the ghost entity's rendering
         * is independent. But to keep Minecraft's entity rendering system
         * happy, keep it close by.
         */
        public void setPositionToPlayer() {
            t = curPlayer.t; // obf: Entity.x
            u = curPlayer.u; // obf: Entity.y
            v = curPlayer.v; // obf: Entity.z
        }
        /**
         * Ensure that the ghost entity will be the last entity to be rendered.
         * Without this, newly-spawned entities may "peek" through rendered
         * shapes depending on the depth function used (e.g., GL_GREATER for
         * shapes that should be visible through terrain).
         * <p>
         * This isn't a huge deal, and in fact it doesn't prevent all rendering
         * glitches: water, clouds, and other things will be rendered after us
         * and can similarly "peek" through shapes depending on the depth func.
         * <p>
         * This is a limitation of the hook we're using. A more elegant
         * solution would be do our rendering after *all* game world elements
         * have been rendered but before the HUD/GUI. Of course (as of
         * Minecraft 1.3) this would involve modifying vanilla classes.
         */
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public void resortInEntityList() {
            if (!GlobalSettings.isGhostEntityUpdateSort()) {
                return;
            }
            List loadedEntities = curWorld.y(); // obf: World.getLoadedEntityList
            if (loadedEntities.get(loadedEntities.size() - 1) != ghostEntity) {
                int index = loadedEntities.indexOf(ghostEntity);
                if (index < 0) {
                    // May be stuck in WorldClient.entitySpawnQueue waiting
                    // for the chunk to load. Try again in a later tick.
                    Controller.getLog().info(getClass().getName() + " ghost entity not spawned yet");
                } else {
                    //Controller.getLog().info(getClass().getName() + " bump");
                    loadedEntities.add(loadedEntities.remove(index));
                }
            }
        }
        /** obf: Entity.entityInit */
        @Override
        protected void a() {
            // do nothing
        }
        /** obf: Entity.readEntityFromNBT */
        @Override
        protected void a(an srcTag) {
            // do nothing
        }
        /** obf: Entity.writeEntityToNBT */
        @Override
        protected void b(an destTag) {
            // do nothing
        }
        /** obf: Entity.isInRangeToRenderVec3D, Vec3 */
        @Override
        public boolean a(ajs vector) {
            return true; // always render
        }
        /** obf: Entity.onUpdate */
        @Override
        public void h_() {
            // do nothing
        }
        /** obf: Entity.setDead */
        @Override
        public void y() {
            // do nothing
        }
        /** obf: Entity.getBrightnessForRender */
        @Override
        public int b(float partialTicks) {
            return 0xf000f0; // max brightness, regardless of the light the ghost entity is actually in
        }
    }

    private Controller controller;
    private Minecraft minecraft;
    private atd curWorld; // obf: WorldClient
    private atg curPlayer; // obf: EntityClientPlayerMP
    private Integer curDimension;
    private GhostEntity ghostEntity; // TODO: expose somewhere, isSpecialEntity(Object)?
    private int ghostEntityUpdateTick;

    public mod_LibShapeDraw() {
        controller = Controller.getInstance();
        controller.initialize(this);
    }

    @Override
    public String getName() {
        return ApiInfo.getName();
    }

    @Override
    public String getVersion() {
        return ApiInfo.getVersion();
    }

    @Override
    public void load() {
        minecraft = ModLoader.getMinecraftInstance();
        ModLoader.registerEntityID(GhostEntity.class, getName(), ModLoader.getUniqueEntityId());
        boolean gameTicksOnly = true;
        ModLoader.setInGameHook(this, true, gameTicksOnly);
        Controller.getLog().info(getClass().getName() + " loaded");
    }

    // obf: NetClientHandler
    @Override
    public void clientConnect(asv netClientHandler) {
        Controller.getLog().info(getClass().getName() + " new server connection");
        curWorld = null;
        curPlayer = null;
        curDimension = null;
        ghostEntity = null;
    }

    @Override
    public boolean onTickInGame(float partialTick, Minecraft minecraft) {
        if (curWorld != minecraft.e || curPlayer != minecraft.g) {
            boolean ghostEntityRespawn = curWorld != minecraft.e;

            curWorld = minecraft.e; // obf: Minecraft.theWorld
            curPlayer = minecraft.g; // obf: Minecraft.thePlayer

            if (ghostEntityRespawn) {
                Controller.getLog().info(getClass().getName() + " respawning ghost entity");
                ghostEntity = new GhostEntity(curWorld);
                ghostEntityUpdateTick = 0;
                minecraft.e.d(ghostEntity); // obf: World.spawnEntityInWorld
            }
            // Else we've respawned back into the same dimension in a
            // single-player world, which reuses its World instance.

            // Dispatch respawn event to Controller.
            int newDimension = curPlayer.bK; // obf: EntityPlayer.dimension
            controller.respawn(getPlayerCoords(partialTick),
                    curDimension == null,
                    curDimension == null || curDimension != newDimension);
            curDimension = newDimension;
        }

        if (ghostEntityUpdateTick-- <= 0) {
            ghostEntityUpdateTick = GlobalSettings.getGhostEntityUpdateTicks();
            ghostEntity.setPositionToPlayer();
            ghostEntity.resortInEntityList();
        }

        // Dispatch game tick event to Controller.
        controller.gameTick(getPlayerCoords(partialTick));

        return true;
    }

    /**
     * Get the player's current coordinates, adjusted for movement that occurs
     * between game ticks.
     */
    private ReadonlyVector3 getPlayerCoords(float partialTick) {
        // obf: Entity.prevPosX, Entity.prevPosY, Entity.prevPosZ
        return new Vector3(
                curPlayer.q + partialTick*(curPlayer.t - curPlayer.q),
                curPlayer.r + partialTick*(curPlayer.u - curPlayer.r),
                curPlayer.s + partialTick*(curPlayer.v - curPlayer.s));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void addRenderer(Map map) {
        Controller.getLog().info(getClass().getName() + " adding ghost entity renderer");
        // obf: RenderEntity
        map.put(GhostEntity.class, new avu() {
            /** obf: Render.doRender, Entity */
            @Override
            public void a(jn entity, double entityX, double entityY, double entityZ, float entityYaw, float partialTick) {
                boolean hideGui = minecraft.y.O; // obf: Minecraft.gameSettings, GameSettings.hideGUI
                // Dispatch render event to Controller.
                controller.render(getPlayerCoords(partialTick), hideGui, false);
            }
        });
    }

    @Override
    public MinecraftAccess startDrawing(int mode) {
        // obf: Tessellator.instance, Tessellator.startDrawing
        ave.a.b(mode);
        return this;
    }

    @Override
    public MinecraftAccess addVertex(double x, double y, double z) {
        // obf: Tessellator.instance, Tessellator.addVertex
        ave.a.a(x, y, z);
        return this;
    }

    @Override
    public MinecraftAccess addVertex(ReadonlyVector3 coords) {
        // obf: Tessellator.instance, Tessellator.addVertex
        ave.a.a(coords.getX(), coords.getY(), coords.getZ());
        return this;
    }

    @Override
    public MinecraftAccess finishDrawing() {
        // obf: Tessellator.instance, Tessellator.draw
        ave.a.a();
        return this;
    }

    @Override
    public MinecraftAccess enableStandardItemLighting() {
        // obf: RenderHelper.enableStandardItemLighting
        ang.b();
        return this;
    }
}
