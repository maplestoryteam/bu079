package server.life;

import handling.world.MaplePartyCharacter;
import client.inventory.IItem;
import client.inventory.Item;
import server.MapleItemInformationProvider;
import client.inventory.Equip;
import client.inventory.MapleInventoryType;
import server.Randomizer;
import java.util.Collections;
import handling.world.MapleParty;
import tools.Pair;
import server.Timer.MobTimer;
import client.ISkill;
import client.SkillFactory;
import server.MapleStatEffect;
import java.util.Map.Entry;
import server.maps.MapleMapObjectType;
import server.maps.MapScriptMethods;
import client.MapleClient;
import java.util.List;
import tools.MaplePacketCreator;
import java.util.ArrayList;
import server.maps.MapleMapObject;
import handling.channel.ChannelServer;
import constants.GameConstants;
import client.MapleDisease;
import client.MapleBuffStat;
import gui.Start;
import java.util.Iterator;
import tools.packet.MobPacket;
import java.util.HashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.LinkedList;
import client.status.MonsterStatusEffect;
import client.status.MonsterStatus;
import java.util.EnumMap;
import scripting.EventInstanceManager;
import java.util.Collection;
import client.MapleCharacter;
import java.lang.ref.WeakReference;
import server.maps.MapleMap;

public class MapleMonster extends AbstractLoadedMapleLife
{
    private MapleMonsterStats stats;
    private OverrideMonsterStats ostats;
    private long hp;
    private long nextKill;
    private int mp;
    private byte venom_counter;
    private byte carnivalTeam;
    private MapleMap map;
    private WeakReference<MapleMonster> sponge;
    private int linkoid;
    private int lastNode;
    private int lastNodeController;
    private int highestDamageChar;
    private WeakReference<MapleCharacter> controller;
    private boolean fake;
    private boolean dropsDisabled;
    private boolean controllerHasAggro;
    private boolean controllerKnowsAboutAggro;
    private final Collection<AttackerEntry> attackers;
    private EventInstanceManager eventInstance;
    private MonsterListener listener;
    private byte[] reflectpack;
    private byte[] nodepack;
    private final EnumMap<MonsterStatus, MonsterStatusEffect> stati;
    private final LinkedList<MonsterStatusEffect> poisons;
    private final ReentrantReadWriteLock poisonsLock;
    private Map<Integer, Long> usedSkills;
    private int stolen;
    private ScheduledFuture<?> dropItemSchedule;
    private boolean shouldDropItem;
    private long lastAbsorbMP;
    
    public MapleMonster(final int id, final MapleMonsterStats stats) {
        super(id);
        this.ostats = null;
        this.sponge = new WeakReference(null);
        this.linkoid = 0;
        this.lastNode = -1;
        this.lastNodeController = -1;
        this.highestDamageChar = 0;
        this.controller = new WeakReference(null);
        this.attackers = (Collection<AttackerEntry>)new LinkedList();
        this.listener = null;
        this.reflectpack = null;
        this.nodepack = null;
        this.stati = new EnumMap(MonsterStatus.class);
        this.poisons = (LinkedList<MonsterStatusEffect>)new LinkedList();
        this.poisonsLock = new ReentrantReadWriteLock();
        this.stolen = -1;
        this.shouldDropItem = false;
        this.initWithStats(stats);
    }
    
    public final int getMobLevel() {
        if (this.ostats != null) {
            return this.ostats.getlevel();
        }
        return this.stats.getLevel();
    }
    
    public MapleMonster(final MapleMonster monster) {
        super((AbstractLoadedMapleLife)monster);
        this.ostats = null;
        this.sponge = new WeakReference(null);
        this.linkoid = 0;
        this.lastNode = -1;
        this.lastNodeController = -1;
        this.highestDamageChar = 0;
        this.controller = new WeakReference(null);
        this.attackers = (Collection<AttackerEntry>)new LinkedList();
        this.listener = null;
        this.reflectpack = null;
        this.nodepack = null;
        this.stati = new EnumMap(MonsterStatus.class);
        this.poisons = (LinkedList<MonsterStatusEffect>)new LinkedList();
        this.poisonsLock = new ReentrantReadWriteLock();
        this.stolen = -1;
        this.shouldDropItem = false;
        this.initWithStats(monster.stats);
    }
    
    public final MapleMonsterStats getStats() {
        return this.stats;
    }
    
    private void initWithStats(final MapleMonsterStats stats) {
        this.setStance(5);
        this.stats = stats;
        this.hp = stats.getHp();
        this.mp = stats.getMp();
        this.venom_counter = 0;
        this.carnivalTeam = -1;
        this.fake = false;
        this.dropsDisabled = false;
        if (stats.getNoSkills() > 0) {
            this.usedSkills = (Map<Integer, Long>)new HashMap();
        }
    }
    
    public final void disableDrops() {
        this.dropsDisabled = true;
    }
    
    public final boolean dropsDisabled() {
        return this.dropsDisabled;
    }
    
    public final void setMap(final MapleMap map) {
        this.map = map;
        this.startDropItemSchedule();
    }
    
    public final MapleMap getMap() {
        return this.map;
    }
    
    public final void setSponge(final MapleMonster mob) {
        this.sponge = new WeakReference(mob);
    }
    
    public final MapleMonster getSponge() {
        return (MapleMonster)this.sponge.get();
    }
    
    public final void setHp(final long hp) {
        this.hp = hp;
    }
    
    public final long getHp() {
        return this.hp;
    }
    
    public final long getMobMaxHp() {
        if (this.ostats != null) {
            return this.ostats.getHp();
        }
        return this.stats.getHp();
    }
    
    public final void setMp(int mp) {
        if (mp < 0) {
            mp = 0;
        }
        this.mp = mp;
    }
    
    public final int getMp() {
        return this.mp;
    }
    
    public final int getMobMaxMp() {
        if (this.ostats != null) {
            return this.ostats.getMp();
        }
        return this.stats.getMp();
    }
    
    public final int getMobExp() {
        if (this.ostats != null) {
            return this.ostats.getExp();
        }
        return this.stats.getExp();
    }
    
    public final void setOverrideStats(final OverrideMonsterStats ostats) {
        this.ostats = ostats;
        this.hp = ostats.getHp();
        this.mp = ostats.getMp();
    }
    
    public final byte getVenomMulti() {
        return this.venom_counter;
    }
    
    public final void setVenomMulti(final byte venom_counter) {
        this.venom_counter = venom_counter;
    }
    
    public final void absorbMP(final int amount) {
        if (!this.canAbsorbMP()) {
            return;
        }
        if (this.getMp() >= amount) {
            this.setMp(this.getMp() - amount);
        }
        else {
            this.setMp(0);
        }
        this.lastAbsorbMP = System.currentTimeMillis();
    }
    
    public final long getLastAbsorbMP() {
        return this.lastAbsorbMP;
    }
    
    public final boolean canAbsorbMP() {
        return System.currentTimeMillis() - this.lastAbsorbMP > 10000L;
    }
    
    public final void damage(final MapleCharacter from, final long damage, final boolean updateAttackTime) {
        this.damage(from, damage, updateAttackTime, 0);
    }
    
    public final void damage(final MapleCharacter from, final long damage, final boolean updateAttackTime, final int lastSkill) {
        if (from == null || damage <= 0L || !this.isAlive()) {
            return;
        }
        AttackerEntry attacker = (from.getParty() != null) ? new PartyAttackerEntry(from.getParty().getId(), this.map.getChannel()) : new SingleAttackerEntry(from, this.map.getChannel());
        boolean replaced = false;
        for (final AttackerEntry aentry : this.attackers) {
            if (aentry.equals(attacker)) {
                attacker = aentry;
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            this.attackers.add(attacker);
        }
        final long rightDamage = Math.max(0L, Math.min(damage, this.hp));
        attacker.addDamage(from, rightDamage, updateAttackTime);
        if (this.getStats().getSelfD() != -1) {
            final long newHp = this.getHp() - rightDamage;
            this.setHp(newHp);
            if (this.getHp() > 0L) {
                if (this.getHp() < (long)this.getStats().getSelfDHp()) {
                    this.getMap().killMonster(this, from, false, false, this.getStats().getSelfD(), lastSkill);
                }
                else {
                    for (final AttackerEntry mattacker : this.attackers) {
                        for (final AttackingMapleCharacter cattacker : mattacker.getAttackers()) {
                            if (cattacker != null && cattacker.getAttacker().getMap() == from.getMap() && cattacker.getLastAttackTime() >= System.currentTimeMillis() - 4000L) {
                                cattacker.getAttacker().getClient().sendPacket(MobPacket.showMonsterHP(this.getObjectId(), (int)Math.ceil((double)this.hp * 100.0 / (double)this.getMobMaxHp())));
                            }
                        }
                    }
                }
            }
            else {
                this.getMap().killMonster(this, from, true, false, (byte)1, lastSkill);
            }
        }
        else {
            if (this.getSponge() != null && this.getSponge().getHp() > 0L) {
                final long newHp = this.getSponge().getHp() - rightDamage;
                this.getSponge().setHp(newHp);
                if (this.getSponge().getHp() <= 0L) {
                    this.getMap().killMonster((MapleMonster)this.sponge.get(), from, true, false, (byte)1, lastSkill);
                }
                else {
                    this.getMap().broadcastMessage(MobPacket.showBossHP((MapleMonster)this.sponge.get()));
                }
            }
            if (this.getHp() > 0L) {
                final long newHp = this.getHp() - rightDamage;
                this.setHp(newHp);
                if (this.eventInstance != null) {
                    this.eventInstance.monsterDamaged(from, this, (int)rightDamage);
                }
                else {
                    final EventInstanceManager em = from.getEventInstance();
                    if (em != null) {
                        em.monsterDamaged(from, this, (int)rightDamage);
                    }
                }
                if (this.getSponge() == null && this.hp > 0L) {
                    switch (this.getStats().getHPDisplayType()) {
                        case 0: {
                            this.getMap().broadcastMessage(MobPacket.showBossHP(this), this.getPosition());
                            break;
                        }
                        case 1: {
                            this.getMap().broadcastMessage(MobPacket.damageFriendlyMob(this, damage, true));
                            break;
                        }
                        case -1:
                        case 2: {
                            this.getMap().broadcastMessage(MobPacket.showMonsterHP(this.getObjectId(), (int)Math.ceil((double)this.hp * 100.0 / (double)this.getMobMaxHp())));
                            from.mulungEnergyModify(true);
                            break;
                        }
                        case 3: {
                            for (final AttackerEntry mattacker : this.attackers) {
                                for (final AttackingMapleCharacter cattacker : mattacker.getAttackers()) {
                                    if (cattacker != null && cattacker.getAttacker().getMap() == from.getMap() && cattacker.getLastAttackTime() >= System.currentTimeMillis() - 4000L) {
                                        cattacker.getAttacker().getClient().sendPacket(MobPacket.showMonsterHP(this.getObjectId(), (int)Math.ceil((double)this.hp * 100.0 / (double)this.getMobMaxHp())));
                                    }
                                }
                            }
                            break;
                        }
                    }
                }
                if (this.getHp() <= 0L) {
                    if (this.getStats().getHPDisplayType() == -1) {
                        this.getMap().broadcastMessage(MobPacket.showMonsterHP(this.getObjectId(), (int)Math.ceil((double)this.hp * 100.0 / (double)this.getMobMaxHp())));
                    }
                    this.getMap().killMonster(this, from, true, false, (byte)1, lastSkill);
                }
            }
        }
        this.startDropItemSchedule();
    }
    
    public final void heal(final int hp, final int mp, final boolean broadcast) {
        long totalHP = this.getHp() + (long)hp;
        int totalMP = this.getMp() + mp;
        totalHP = ((totalHP > this.getMobMaxHp()) ? this.getMobMaxHp() : totalHP);
        totalMP = ((totalMP > this.getMobMaxMp()) ? this.getMobMaxMp() : totalMP);
        this.setHp(totalHP);
        this.setMp(totalMP);
        if (broadcast) {
            this.getMap().broadcastMessage(MobPacket.healMonster(this.getObjectId(), hp));
        }
        if (this.getSponge() != null) {
            totalHP = this.getSponge().getHp() + (long)hp;
            totalMP = this.getSponge().getMp() + mp;
            totalHP = ((totalHP > this.getSponge().getMobMaxHp()) ? this.getSponge().getMobMaxHp() : totalHP);
            totalMP = ((totalMP > this.getSponge().getMobMaxMp()) ? this.getSponge().getMobMaxMp() : totalMP);
            this.getSponge().setHp(totalHP);
            this.getSponge().setMp(totalMP);
        }
    }
    
    public void giveExpToCharacter(final MapleCharacter attacker, int exp, final boolean highestDamage, final int numExpSharers, final byte pty, final byte classBounsExpPercent, final byte Premium_Bonus_EXP_PERCENT, final int lastskillID) {
        if (highestDamage) {
            if (this.eventInstance != null) {
                this.eventInstance.monsterKilled(attacker, this);
            }
            else {
                final EventInstanceManager em = attacker.getEventInstance();
                if (em != null) {
                    em.monsterKilled(attacker, this);
                }
            }
            this.highestDamageChar = attacker.getId();
        }
        if (exp > 0) {
            if (Start.ConfigValuesMap.get("越级打怪开关") != null && ((Integer)Start.ConfigValuesMap.get("越级打怪开关")).intValue() < 1) {
                final int 怪物 = this.getMobLevel();
                final int 玩家 = attacker.getLevel();
                if (玩家 < 怪物) {
                    final int 相差 = 怪物 - 玩家;
                    if (相差 >= 10 && 相差 <= 20) {
                        exp = (int)((double)exp * 0.6);
                    }
                    else if (相差 >= 21 && 相差 <= 30) {
                        exp = (int)((double)exp * 0.4);
                    }
                    else if (相差 >= 31) {
                        exp = (int)((double)exp * 0.2);
                    }
                    else {
                        exp = exp;
                    }
                }
                else {
                    exp = exp;
                }
            }
            final MonsterStatusEffect mse = (MonsterStatusEffect)this.stati.get(MonsterStatus.SHOWDOWN);
            if (mse != null) {
                exp += (int)((double)exp * ((double)mse.getX().intValue() / 100.0));
            }
            final Integer holySymbol = attacker.getBuffedValue(MapleBuffStat.HOLY_SYMBOL);
            if (holySymbol != null) {
                if (numExpSharers == 1) {
                    exp = (int)((double)exp * (1.0 + holySymbol.doubleValue() / 500.0));
                }
                else {
                    exp = (int)((double)exp * (1.0 + holySymbol.doubleValue() / 100.0));
                }
            }
            if (attacker.hasDisease(MapleDisease.CURSE)) {
                exp /= 2;
            }
            final double lastexp = (attacker.getStat().realExpBuff - 100.0 <= 0.0) ? 100.0 : (attacker.getStat().realExpBuff - 100.0);
            exp *= attacker.getEXPMod() * (int)(lastexp / 100.0);
            exp = Math.min(Integer.MAX_VALUE, exp * ((attacker.getLevel() <= 255) ? GameConstants.getExpRate_Below10((int)attacker.getJob(), attacker) : ChannelServer.getInstance(this.map.getChannel()).getExpRate()));
            int 活动经验 = 0;
            int classBonusExp = 0;
            if (classBounsExpPercent > 0) {
                classBonusExp = (int)((double)exp / 100.0 * (double)classBounsExpPercent);
            }
            int premiumBonusExp = 0;
            if (Premium_Bonus_EXP_PERCENT > 0) {
                premiumBonusExp = (int)((double)exp / 100.0 * (double)Premium_Bonus_EXP_PERCENT);
            }
            int equpBonusExp = (int)((double)exp / 100.0 * (double)attacker.getStat().equipmentBonusExp);
            if (attacker.getStat().equippedFairy) {
                equpBonusExp += (int)((double)exp / 100.0 * (double)attacker.getFairyExp());
            }
            int wedding_EXP = 0;
            if (attacker.getMarriageId() > 0 && attacker.getMap().getCharacterById_InMap(attacker.getMarriageId()) != null) {
                wedding_EXP += (int)((double)exp / 100.0 * 10.0);
            }
            attacker.gainExpMonster(exp, true, highestDamage, pty, wedding_EXP, classBonusExp, equpBonusExp, premiumBonusExp,活动经验);
        }
        attacker.mobKilled(this.getId(), lastskillID);
    }
    
    public final int killBy(final MapleCharacter killer, final int lastSkill) {
        final int totalBaseExp = this.getMobExp();
        AttackerEntry highest = null;
        long highdamage = 0L;
        for (final AttackerEntry attackEntry : this.attackers) {
            if (attackEntry.getDamage() > highdamage) {
                highest = attackEntry;
                highdamage = attackEntry.getDamage();
            }
        }
        for (final AttackerEntry attackEntry : this.attackers) {
            final int baseExp = (int)Math.ceil((double)totalBaseExp * ((double)attackEntry.getDamage() / (double)this.getMobMaxHp()));
            attackEntry.killedMob(this.getMap(), baseExp, attackEntry == highest, lastSkill);
        }
        final MapleCharacter controll = this.getController();
        if (controll != null) {
            controll.getClient().sendPacket(MobPacket.stopControllingMonster(this.getObjectId()));
            controll.stopControllingMonster(this);
        }
        switch (this.getId()) {
            default: {
                this.spawnRevives(this.getMap());
                if (this.eventInstance != null) {
                    this.eventInstance.unregisterMonster(this);
                    this.eventInstance = null;
                }
                if (killer != null && killer.getPyramidSubway() != null) {
                    killer.getPyramidSubway().onKill(killer);
                }
                final MapleMonster oldSponge = this.getSponge();
                this.sponge = new WeakReference(null);
                if (oldSponge != null && oldSponge.isAlive()) {
                    boolean set = true;
                    for (final MapleMapObject mon : this.map.getAllMonstersThreadsafe()) {
                        final MapleMonster mons = (MapleMonster)mon;
                        if (mons.getObjectId() != oldSponge.getObjectId() && mons.getObjectId() != this.getObjectId() && (mons.getSponge() == oldSponge || mons.getLinkOid() == oldSponge.getObjectId())) {
                            set = false;
                            break;
                        }
                    }
                    if (set) {
                        this.map.killMonster(oldSponge, killer, true, false, (byte)1);
                    }
                }
                this.nodepack = null;
                this.reflectpack = null;
                this.stati.clear();
                this.cancelDropItem();
                if (this.listener != null) {
                    this.listener.monsterKilled();
                }
                final int v1 = this.highestDamageChar;
                this.highestDamageChar = 0;
                return v1;
            }
        }
    }
    
    public final void spawnRevives(final MapleMap map) {
        List<Integer> toSpawn = this.stats.getRevives();
        if (toSpawn == null) {
            return;
        }
        MapleMonster spongy = null;
        long spongyHp = 0L;
        switch (this.getId()) {
            case 8810118:
            case 8810119:
            case 8810120:
            case 8810121: {
                final Iterator<Integer> iterator = toSpawn.iterator();
                while (iterator.hasNext()) {
                    final int i = ((Integer)iterator.next()).intValue();
                    final MapleMonster mob = MapleLifeFactory.getMonster(i);
                    mob.setPosition(this.getPosition());
                    if (this.eventInstance != null) {
                        this.eventInstance.registerMonster(mob);
                    }
                    if (this.dropsDisabled()) {
                        mob.disableDrops();
                    }
                    switch (mob.getId()) {
                        case 8810119:
                        case 8810120:
                        case 8810121:
                        case 8810122: {
                            spongy = mob;
                            continue;
                        }
                    }
                }
                if (spongy != null) {
                    map.spawnRevives(spongy, this.getObjectId());
                    for (final MapleMapObject mon : map.getAllMonstersThreadsafe()) {
                        final MapleMonster mons = (MapleMonster)mon;
                        if (mons.getObjectId() != spongy.getObjectId() && (mons.getSponge() == this || mons.getLinkOid() == this.getObjectId())) {
                            mons.setSponge(spongy);
                            mons.setLinkOid(spongy.getObjectId());
                        }
                    }
                    break;
                }
                break;
            }
            case 8820300:
            case 8820301:
            case 8820302:
            case 8820303: {
                final MapleMonster linkMob = MapleLifeFactory.getMonster(this.getId() - 190);
                if (linkMob != null) {
                    toSpawn = linkMob.getStats().getRevives();
                }
            }
            case 8820108:
            case 8820109: {
                final List<MapleMonster> cs_mobs = (List<MapleMonster>)new ArrayList();
                final Iterator<Integer> iterator3 = toSpawn.iterator();
                while (iterator3.hasNext()) {
                    final int j = ((Integer)iterator3.next()).intValue();
                    final MapleMonster mob2 = MapleLifeFactory.getMonster(j);
                    mob2.setPosition(this.getTruePosition());
                    if (this.eventInstance != null) {
                        this.eventInstance.registerMonster(mob2);
                    }
                    if (this.dropsDisabled()) {
                        mob2.disableDrops();
                    }
                    switch (mob2.getId()) {
                        case 8820109:
                        case 8820300:
                        case 8820301:
                        case 8820302:
                        case 8820303:
                        case 8820304: {
                            spongy = mob2;
                            continue;
                        }
                        default: {
                            if (mob2.isFirstAttack()) {
                                spongyHp += mob2.getMobMaxHp();
                            }
                            cs_mobs.add(mob2);
                            continue;
                        }
                    }
                }
                if (spongy != null && map.getMonsterById(spongy.getId()) == null) {
                    if (spongyHp > 0L) {
                        spongy.setHp(spongyHp);
                        spongy.getStats().setHp(spongyHp);
                    }
                    map.spawnMonster(spongy, -2);
                    for (final MapleMonster k : cs_mobs) {
                        map.spawnMonster(k, -2);
                        k.setSponge(spongy);
                    }
                    break;
                }
                break;
            }
            case 8810026:
            case 8810130:
            case 8820008:
            case 8820009:
            case 8820010:
            case 8820011:
            case 8820012:
            case 8820013: {
                final List<MapleMonster> mobs = (List<MapleMonster>)new ArrayList();
                final Iterator<Integer> iterator5 = toSpawn.iterator();
                while (iterator5.hasNext()) {
                    final int l = ((Integer)iterator5.next()).intValue();
                    final MapleMonster mob3 = MapleLifeFactory.getMonster(l);
                    mob3.setPosition(this.getPosition());
                    if (this.eventInstance != null) {
                        this.eventInstance.registerMonster(mob3);
                    }
                    if (this.dropsDisabled()) {
                        mob3.disableDrops();
                    }
                    switch (mob3.getId()) {
                        case 8810018:
                        case 8810118:
                        case 8820009:
                        case 8820010:
                        case 8820011:
                        case 8820012:
                        case 8820013:
                        case 8820014: {
                            spongy = mob3;
                            continue;
                        }
                        default: {
                            mobs.add(mob3);
                            continue;
                        }
                    }
                }
                if (spongy != null) {
                    map.spawnRevives(spongy, this.getObjectId());
                    for (final MapleMonster m : mobs) {
                        m.setSponge(spongy);
                        map.spawnRevives(m, this.getObjectId());
                    }
                    break;
                }
                break;
            }
            case 8820304: {
                final MapleMonster linkMob_1 = MapleLifeFactory.getMonster(this.getId() - 190);
                if (linkMob_1 != null) {
                    toSpawn = linkMob_1.getStats().getRevives();
                }
            }
            case 8820014:
            case 8820101:
            case 8820200:
            case 8820201:
            case 8820202:
            case 8820203:
            case 8820204:
            case 8820205:
            case 8820206:
            case 8820207:
            case 8820208:
            case 8820209:
            case 8820210:
            case 8820211: {
                final Iterator<Integer> iterator7 = toSpawn.iterator();
                while (iterator7.hasNext()) {
                    final int l = ((Integer)iterator7.next()).intValue();
                    final MapleMonster mob3 = MapleLifeFactory.getMonster(l);
                    if (this.eventInstance != null) {
                        this.eventInstance.registerMonster(mob3);
                    }
                    mob3.setPosition(this.getTruePosition());
                    if (this.dropsDisabled()) {
                        mob3.disableDrops();
                    }
                    map.spawnMonster(mob3, -2);
                }
                break;
            }
            default: {
                final Iterator<Integer> iterator8 = toSpawn.iterator();
                while (iterator8.hasNext()) {
                    final int l = ((Integer)iterator8.next()).intValue();
                    final MapleMonster mob3 = MapleLifeFactory.getMonster(l);
                    if (this.eventInstance != null) {
                        this.eventInstance.registerMonster(mob3);
                    }
                    mob3.setPosition(this.getPosition());
                    if (this.dropsDisabled()) {
                        mob3.disableDrops();
                    }
                    map.spawnRevives(mob3, this.getObjectId());
                    if (mob3.getId() == 9300216) {
                        map.broadcastMessage(MaplePacketCreator.environmentChange("Dojang/clear", 4));
                        map.broadcastMessage(MaplePacketCreator.environmentChange("dojang/end/clear", 3));
                    }
                }
                break;
            }
        }
    }
    
    public final void setCarnivalTeam(final byte team) {
        this.carnivalTeam = team;
    }
    
    public final byte getCarnivalTeam() {
        return this.carnivalTeam;
    }
    
    public final MapleCharacter getController() {
        return (MapleCharacter)this.controller.get();
    }
    
    public final void setController(final MapleCharacter controller) {
        this.controller = new WeakReference(controller);
    }
    
    public final void switchController(final MapleCharacter newController, final boolean immediateAggro) {
        final MapleCharacter controllers = this.getController();
        if (controllers == newController) {
            return;
        }
        if (controllers != null) {
            controllers.stopControllingMonster(this);
            controllers.getClient().sendPacket(MobPacket.stopControllingMonster(this.getObjectId()));
            this.sendStatus(controllers.getClient());
        }
        newController.controlMonster(this, immediateAggro);
        this.setController(newController);
        if (immediateAggro) {
            this.setControllerHasAggro(true);
        }
        this.setControllerKnowsAboutAggro(false);
        if (this.getId() == 9300275 && this.map.getId() >= 921120100 && this.map.getId() < 921120500) {
            if (this.lastNodeController != -1 && this.lastNodeController != newController.getId()) {
                this.resetShammos(newController.getClient());
            }
            else {
                this.setLastNodeController(newController.getId());
            }
        }
    }
    
    public final void resetShammos(final MapleClient c) {
        this.map.killAllMonsters(true);
        this.map.broadcastMessage(MaplePacketCreator.serverNotice(5, "A player has moved too far from Shammos. Shammos is going back to the start."));
        for (final MapleCharacter chr : this.map.getCharactersThreadsafe()) {
            chr.changeMap(chr.getMap(), chr.getMap().getPortal(0));
        }
        MapScriptMethods.startScript_FirstUser(c, "shammos_Fenter");
    }
    
    public final void setListener(final MonsterListener listener) {
        this.listener = listener;
    }
    
    public final boolean isControllerHasAggro() {
        return this.controllerHasAggro;
    }
    
    public final void setControllerHasAggro(final boolean controllerHasAggro) {
        this.controllerHasAggro = controllerHasAggro;
    }
    
    public final boolean isControllerKnowsAboutAggro() {
        return this.controllerKnowsAboutAggro;
    }
    
    public final void setControllerKnowsAboutAggro(final boolean controllerKnowsAboutAggro) {
        this.controllerKnowsAboutAggro = controllerKnowsAboutAggro;
    }
    
    public final void sendStatus(final MapleClient client) {
        if (this.reflectpack != null) {
            client.getSession().writeAndFlush(this.reflectpack);
        }
        if (this.poisons.size() > 0) {
            this.poisonsLock.readLock().lock();
            try {
                client.getSession().writeAndFlush(MobPacket.applyMonsterStatus(this, this.poisons));
            }
            finally {
                this.poisonsLock.readLock().unlock();
            }
        }
    }
    
    @Override
    public final void sendSpawnData(final MapleClient client) {
        if (!this.isAlive()) {
            return;
        }
        client.sendPacket(MobPacket.spawnMonster(this, (this.lastNode >= 0) ? -2 : -1, this.fake ? 252 : ((this.lastNode >= 0) ? 12 : 0), 0));
        this.sendStatus(client);
        if (this.lastNode >= 0) {
            client.sendPacket(MaplePacketCreator.getNodeProperties(this, this.map));
            if (this.getId() == 9300275 && this.map.getId() >= 921120100 && this.map.getId() < 921120500) {
                if (this.lastNodeController != -1) {
                    this.resetShammos(client);
                }
                else {
                    this.setLastNodeController(client.getPlayer().getId());
                }
            }
        }
    }
    
    @Override
    public final void sendDestroyData(final MapleClient client) {
        if (this.lastNode == -1) {
            client.sendPacket(MobPacket.killMonster(this.getObjectId(), 0));
        }
        if (this.getId() == 9300275 && this.map.getId() >= 921120100 && this.map.getId() < 921120500) {
            this.resetShammos(client);
        }
    }
    
    @Override
    public final String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(this.stats.getName());
        sb.append("(");
        sb.append(this.getId());
        sb.append(") (等级 ");
        sb.append((int)this.stats.getLevel());
        sb.append(") 在 (X");
        sb.append(this.getPosition().x);
        sb.append("/ Y");
        sb.append(this.getPosition().y);
        sb.append(") 座标 ");
        sb.append(this.getHp());
        sb.append("/ ");
        sb.append(this.getMobMaxHp());
        sb.append("血量, ");
        sb.append(this.getMp());
        sb.append("/ ");
        sb.append(this.getMobMaxMp());
        sb.append(" 魔力, MobOID: ");
        sb.append(this.getObjectId());
        sb.append(" || 仇恨目标 : ");
        final MapleCharacter chr = (MapleCharacter)this.controller.get();
        sb.append((chr != null) ? chr.getName() : "无");
        return sb.toString();
    }
    
    @Override
    public final MapleMapObjectType getType() {
        return MapleMapObjectType.MONSTER;
    }
    
    public final void setEventInstance(final EventInstanceManager eventInstance) {
        this.eventInstance = eventInstance;
    }
    
    public final EventInstanceManager getEventInstance() {
        return this.eventInstance;
    }
    
    public final int getStatusSourceID(final MonsterStatus status) {
        final MonsterStatusEffect effect = (MonsterStatusEffect)this.stati.get(status);
        if (effect != null) {
            return effect.getSkill();
        }
        return -1;
    }
    
    public final ElementalEffectiveness getEffectiveness(final Element e) {
        if (this.stati.size() > 0 && this.stati.get(MonsterStatus.DOOM) != null) {
            return ElementalEffectiveness.NORMAL;
        }
        return this.stats.getEffectiveness(e);
    }
    
    public void applyMonsterBuff(final Map<MonsterStatus, Integer> effect, final int x, final int skillId, final long duration, final MobSkill skill, final List<Integer> reflection) {
        final MapleCharacter con = this.getController();
        for (final Entry<MonsterStatus, Integer> z : effect.entrySet()) {
            if (this.stati.containsKey(z.getKey())) {
                this.cancelStatus((MonsterStatus)z.getKey());
            }
            final MonsterStatusEffect effectz = new MonsterStatusEffect((MonsterStatus)z.getKey(), (Integer)z.getValue(), 0, skill, true, reflection.size() > 0);
            effectz.setCancelTask(duration);
            this.stati.put((MonsterStatus)z.getKey(), effectz);
        }
        if (reflection.size() > 0) {
            final List<MonsterStatusEffect> mse = (List<MonsterStatusEffect>)new ArrayList();
            for (final Entry<MonsterStatus, Integer> z2 : effect.entrySet()) {
                mse.add(new MonsterStatusEffect((MonsterStatus)z2.getKey(), (Integer)z2.getValue(), 0, skill, true, reflection.size() > 0));
            }
            this.reflectpack = MobPacket.applyMonsterStatus(this, mse);
            if (con != null) {
                this.map.broadcastMessage(con, this.reflectpack, this.getTruePosition());
                con.getClient().getSession().writeAndFlush(this.reflectpack);
            }
            else {
                this.map.broadcastMessage(this.reflectpack, this.getTruePosition());
            }
        }
        else {
            for (final Entry<MonsterStatus, Integer> z : effect.entrySet()) {
                final MonsterStatusEffect effectz = new MonsterStatusEffect((MonsterStatus)z.getKey(), (Integer)z.getValue(), 0, skill, true, reflection.size() > 0);
                if (con != null) {
                    this.map.broadcastMessage(con, MobPacket.applyMonsterStatus(this, effectz), this.getTruePosition());
                    con.getClient().getSession().writeAndFlush(MobPacket.applyMonsterStatus(this, effectz));
                }
                else {
                    this.map.broadcastMessage(MobPacket.applyMonsterStatus(this, effectz), this.getTruePosition());
                }
            }
        }
    }
    
    public final void applyStatus(final MapleCharacter from, final MonsterStatusEffect status, final boolean poison, long duration, final boolean checkboss, final MapleStatEffect eff) {
        if (!this.isAlive()) {
            return;
        }
        if (from.hasGmLevel(5) && status.getStatus() != null) {
            from.dropMessage(6, "怪物: " + this.getId() + " 状态: " + status.getStatus().name() + " 是否为毒: " + poison + " 持续时间: " + duration + " 技能: " + SkillFactory.getSkillName(eff.getSourceId()));
        }
        final ISkill skilz = SkillFactory.getSkill(status.getSkill());
        if (skilz != null) {
            switch (this.stats.getEffectiveness(skilz.getElement())) {
                case IMMUNE:
                case STRONG: {
                    return;
                }
                case NORMAL:
                case WEAK: {
                    break;
                }
                default: {
                    return;
                }
            }
        }
        final int statusSkill = status.getSkill();
        switch (statusSkill) {
        case 2111006:
          switch (this.stats.getEffectiveness(Element.POISON)) {
            case IMMUNE:
            case STRONG:
              return;
          } 
          break;
        case 2211006:
          switch (this.stats.getEffectiveness(Element.ICE)) {
            case IMMUNE:
            case STRONG:
              return;
          } 
          break;
        case 4120005:
        case 4220005:
        case 14110004:
          switch (this.stats.getEffectiveness(Element.POISON)) {
            case WEAK:
              return;
          } 
          break;
      } 
        if (duration >= 2000000000L) {
            duration = 5000L;
        }
        final MonsterStatus stat = status.getStatus();
        if (this.getId() == 5100002 && stat == MonsterStatus.POISON) {
            return;
        }
        if (this.stats.isNoDoom() && stat == MonsterStatus.DOOM) {
            return;
        }
        if (stat == MonsterStatus.FREEZE) {
            switch (this.getId()) {
                case 9400253:
                case 9400254: {
                    return;
                }
            }
        }
        if (this.stats.isBoss()) {
            if (stat == MonsterStatus.POISON) {
                return;
            }
            if (stat == MonsterStatus.STUN) {
                return;
            }
            if (stat != MonsterStatus.SPEED && stat != MonsterStatus.NINJA_AMBUSH && stat != MonsterStatus.WATK) {
                return;
            }
            if (this.getId() == 8850011 && stat == MonsterStatus.MAGIC_CRASH) {
                return;
            }
            if (stat == MonsterStatus.FREEZE) {
                return;
            }
        }
        if ((this.stats.isFriendly() || this.isFake()) && (stat == MonsterStatus.STUN || stat == MonsterStatus.SPEED || stat == MonsterStatus.POISON || stat == MonsterStatus.VENOMOUS_WEAPON)) {
            return;
        }
        if ((stat == MonsterStatus.VENOMOUS_WEAPON || stat == MonsterStatus.POISON) && eff == null) {
            return;
        }
        if (this.stati.containsKey(stat)) {
            return;
        }
        if (stat == MonsterStatus.POISON || stat == MonsterStatus.VENOMOUS_WEAPON) {
            this.poisonsLock.readLock().lock();
            try {
                for (final MonsterStatusEffect mse : this.poisons) {
                    if (mse == null || mse.getSkill() == eff.getSourceId() || mse.getSkill() == GameConstants.getLinkedAttackSkill(eff.getSourceId()) || GameConstants.getLinkedAttackSkill(mse.getSkill()) == eff.getSourceId()) {}
                }
            }
            finally {
                this.poisonsLock.readLock().unlock();
            }
        }
        if (poison && this.getHp() > 1L && eff != null) {
            duration = Math.max(duration, (long)(eff.getDOTTime() * 1000));
        }
        final long aniTime;
        duration = (aniTime = duration + (long)(from.getStat().dotTime * 1000));
        status.setCancelTask(aniTime);
        if (poison && this.getHp() > 1L) {
            if (status.getchr() != null) {
                return;
            }
            status.setDotTime(duration);
            int dam = (int)Math.min(32767L, (long)((double)this.getMobMaxHp() / (70.0 - (double)from.getSkillLevel(status.getSkill())) + 0.999));
            if (dam > 0 && (long)dam >= this.hp) {
                dam = (int)(this.hp - 1L);
            }
            if (from.hasGmLevel(5)) {
                from.dropMessage(6, "[持续伤害] 开始处理效果 - 技能ID：" + eff.getSourceId());
            }
            status.setValue(status.getStatus(), Integer.valueOf(dam));
            status.setPoisonDamage(dam, from);
            final int poisonDamage = (int)(aniTime / 1000L * (long)status.getX().intValue());
            if (from.hasGmLevel(5)) {
                from.dropMessage(6, "[持续伤害] 持续伤害： " + ((this.getHp() > (long)poisonDamage) ? ((long)poisonDamage) : (this.getHp() - 1L)) + " 持续时间：" + aniTime + " 持续掉血：" + status.getX());
            }
        }
        else if (statusSkill == 5211004 && this.getHp() > 1L) {
            if (status.getchr() != null) {
                return;
            }
            status.setDotTime(duration);
            final int dam = (int)Math.min(32767L, (long)((double)this.getMobMaxHp() / (70.0 - (double)from.getSkillLevel(status.getSkill())) + 0.999));
            if (from.isAdmin()) {
                from.dropMessage(6, "[持续伤害] 开始处理效果 - 技能ID：" + eff.getSourceId());
            }
            status.setValue(status.getStatus(), Integer.valueOf(dam));
            status.setPoisonDamage(dam, from);
            final int poisonDamage = (int)(aniTime / 1000L * (long)status.getX().intValue());
            if (from.isAdmin()) {
                from.dropMessage(6, "[持续伤害] 持续伤害： " + ((this.getHp() > (long)poisonDamage) ? ((long)poisonDamage) : (this.getHp() - 1L)) + " 持续时间：" + aniTime + " 持续掉血：" + status.getX());
            }
        }
        else if (statusSkill == 4111003 || statusSkill == 14111001) {
            status.setValue(status.getStatus(), Integer.valueOf((int)((double)this.getMobMaxHp() / 50.0 + 0.999)));
            status.setPoisonDamage(status.getX().intValue(), from);
        }
        else if (statusSkill == 4341003) {
            status.setPoisonDamage((int)((double)((float)eff.getDamage() * from.getStat().getCurrentMaxBaseDamage()) / 100.0), from);
        }
        else if (statusSkill == 4121004 || statusSkill == 4221004) {
            status.setValue(status.getStatus(), Integer.valueOf(Math.min(32767, (int)((double)((float)eff.getDamage() * from.getStat().getCurrentMaxBaseDamage()) / 100.0))));
            int dam = (int)(aniTime / 1000L * (long)status.getX().intValue() / 2L);
            status.setPoisonDamage(dam, from);
            if (dam > 0) {
                if ((long)dam >= this.hp) {
                    dam = (int)(this.hp - 1L);
                }
                this.damage(from, (long)dam, false);
            }
        }
        final MapleCharacter con = this.getController();
        if (stat == MonsterStatus.POISON || stat == MonsterStatus.VENOMOUS_WEAPON) {
            this.poisonsLock.writeLock().lock();
            try {
                this.poisons.add(status);
                status.scheduledoPoison(this);
            }
            finally {
                this.poisonsLock.writeLock().unlock();
            }
        }
        else {
            this.stati.put(stat, status);
        }
        if (con != null) {
            this.map.broadcastMessage(con, MobPacket.applyMonsterStatus(this, status), this.getTruePosition());
            con.getClient().sendPacket(MobPacket.applyMonsterStatus(this, status));
        }
        else {
            this.map.broadcastMessage(MobPacket.applyMonsterStatus(this, status), this.getTruePosition());
        }
        if (from.getDebugMessage()) {
            from.dropMessage(6, "开始 => 给予怪物状态: 持续时间[" + aniTime + "] 状态效果[" + status.getStatus().name() + "] 开始时间[" + System.currentTimeMillis() + "]");
        }
    }
    
    public final void dispelSkill(final MobSkill skillId) {
        final List<MonsterStatus> toCancel = (List<MonsterStatus>)new ArrayList();
        for (final Entry<MonsterStatus, MonsterStatusEffect> effects : this.stati.entrySet()) {
            final MonsterStatusEffect mse = (MonsterStatusEffect)effects.getValue();
            if (mse != null && mse.getMobSkill() != null && mse.getMobSkill().getSkillId() == skillId.getSkillId()) {
                toCancel.add(effects.getKey());
            }
        }
        for (final MonsterStatus stat : toCancel) {
            this.cancelStatus(stat);
        }
    }
    
    public final void cancelStatus(final MonsterStatus stat) {
        if (stat == MonsterStatus.EMPTY || stat == MonsterStatus.SUMMON) {
            return;
        }
        final MonsterStatusEffect mse = (MonsterStatusEffect)this.stati.get(stat);
        if (mse == null || !this.isAlive()) {
            return;
        }
        if (mse.isReflect()) {
            this.reflectpack = null;
        }
        mse.cancelPoisonSchedule(this);
        final MapleCharacter con = this.getController();
        if (con != null) {
            this.map.broadcastMessage(con, MobPacket.cancelMonsterStatus(this, mse), this.getTruePosition());
            con.getClient().sendPacket(MobPacket.cancelMonsterStatus(this, mse));
        }
        else {
            this.map.broadcastMessage(MobPacket.cancelMonsterStatus(this, mse), this.getTruePosition());
        }
        this.stati.remove(stat);
    }
    
    public final void cancelSingleStatus(final MonsterStatusEffect stat) {
        if (stat == null || stat.getStatus() == MonsterStatus.EMPTY || stat.getStatus() == MonsterStatus.SUMMON || !this.isAlive()) {
            return;
        }
        if (stat.getStatus() != MonsterStatus.POISON && stat.getStatus() != MonsterStatus.VENOMOUS_WEAPON) {
            this.cancelStatus(stat.getStatus());
            return;
        }
        this.poisonsLock.writeLock().lock();
        try {
            if (!this.poisons.contains(stat)) {
                return;
            }
            this.poisons.remove(stat);
            if (stat.isReflect()) {
                this.reflectpack = null;
            }
            stat.cancelPoisonSchedule(this);
            final MapleCharacter con = this.getController();
            if (con != null) {
                this.map.broadcastMessage(con, MobPacket.cancelMonsterStatus(this, stat), this.getTruePosition());
                con.getClient().getSession().writeAndFlush(MobPacket.cancelMonsterStatus(this, stat));
            }
            else {
                this.map.broadcastMessage(MobPacket.cancelMonsterStatus(this, stat), this.getTruePosition());
            }
        }
        finally {
            this.poisonsLock.writeLock().unlock();
        }
    }
    
    public final void doPoison(final MonsterStatusEffect status, final WeakReference<MapleCharacter> weakChr) {
        if ((status.getStatus() == MonsterStatus.VENOMOUS_WEAPON || status.getStatus() == MonsterStatus.POISON || status.getStatus() == MonsterStatus.NEUTRALISE) && this.poisons.size() <= 0) {
            return;
        }
        if (status.getStatus() != MonsterStatus.VENOMOUS_WEAPON && status.getStatus() != MonsterStatus.POISON && status.getStatus() == MonsterStatus.NEUTRALISE && !this.stati.containsKey(status.getStatus())) {
            return;
        }
        if (weakChr == null) {
            return;
        }
        int damage = status.getPoisonDamage();
        final boolean shadowWeb = status.getSkill() == 4111003 || status.getSkill() == 14111001;
        final MapleCharacter chr = (MapleCharacter)weakChr.get();
        boolean cancel = damage <= 0 || chr == null || chr.getMapId() != this.map.getId();
        if ((long)damage >= this.hp) {
            damage = (int)this.hp - 1;
            cancel = (!shadowWeb || cancel);
        }
        if (!cancel) {
            this.damage(chr, (long)damage, false);
            if (shadowWeb) {
                this.map.broadcastMessage(MobPacket.damageMonster(this.getObjectId(), (long)damage), this.getTruePosition());
            }
        }
    }
    
    public final void setTempEffectiveness(final Element e, final long milli) {
        this.stats.setEffectiveness(e, ElementalEffectiveness.WEAK);
        MobTimer.getInstance().schedule((Runnable)new Runnable() {
            @Override
            public void run() {
                MapleMonster.this.stats.removeEffectiveness(e);
            }
        }, milli);
    }
    
    public final boolean isBuffed(final MonsterStatus status) {
        return this.stati.containsKey(status);
    }
    
    public final MonsterStatusEffect getBuff(final MonsterStatus status) {
        return (MonsterStatusEffect)this.stati.get(status);
    }
    
    public final int getStatiSize() {
        return this.stati.size() + ((this.poisons.size() > 0) ? 1 : 0);
    }
    
    public final ArrayList<MonsterStatusEffect> getAllBuffs() {
        final ArrayList<MonsterStatusEffect> ret = (ArrayList<MonsterStatusEffect>)new ArrayList();
        for (final MonsterStatusEffect e : this.stati.values()) {
            ret.add(e);
        }
        this.poisonsLock.readLock().lock();
        try {
            for (final MonsterStatusEffect e : this.poisons) {
                ret.add(e);
            }
        }
        finally {
            this.poisonsLock.readLock().unlock();
        }
        return ret;
    }
    
    public final void setFake(final boolean fake) {
        this.fake = fake;
    }
    
    public final boolean isFake() {
        return this.fake;
    }
    
    public final boolean isAlive() {
        return this.hp > 0L;
    }
    
    public boolean isAttackedBy(final MapleCharacter chr) {
        for (final AttackerEntry aentry : this.attackers) {
            if (aentry.contains(chr)) {
                return true;
            }
        }
        return false;
    }
    
    public final boolean isFirstAttack() {
        return this.stats.isFirstAttack();
    }
    
    public final List<Pair<Integer, Integer>> getSkills() {
        return this.stats.getSkills();
    }
    
    public final boolean hasSkill(final int skillId, final int level) {
        return this.stats.hasSkill(skillId, level);
    }
    
    public final long getLastSkillUsed(final int skillId) {
        if (this.usedSkills.containsKey(Integer.valueOf(skillId))) {
            return ((Long)this.usedSkills.get(Integer.valueOf(skillId))).longValue();
        }
        return 0L;
    }
    
    public final void setLastSkillUsed(final int skillId, final long now, final long cooltime) {
        switch (skillId) {
            case 140: {
                this.usedSkills.put(Integer.valueOf(skillId), Long.valueOf(now + cooltime * 2L));
                this.usedSkills.put(Integer.valueOf(141), Long.valueOf(now));
                break;
            }
            case 141: {
                this.usedSkills.put(Integer.valueOf(skillId), Long.valueOf(now + cooltime * 2L));
                this.usedSkills.put(Integer.valueOf(140), Long.valueOf(now + cooltime));
                break;
            }
            default: {
                this.usedSkills.put(Integer.valueOf(skillId), Long.valueOf(now + cooltime));
                break;
            }
        }
    }
    
    public final byte getNoSkills() {
        return this.stats.getNoSkills();
    }
    
    public final int getBuffToGive() {
        return this.stats.getBuffToGive();
    }
    
    public int getLevel() {
        return this.stats.getLevel();
    }
    
    public int getLinkOid() {
        return this.linkoid;
    }
    
    public void setLinkOid(final int lo) {
        this.linkoid = lo;
    }
    
    public final Map<MonsterStatus, MonsterStatusEffect> getStati() {
        return this.stati;
    }
    
    public void addEmpty() {
        for (final MonsterStatus stat : MonsterStatus.values()) {
            if (stat.isDefault()) {
                this.stati.put(stat, new MonsterStatusEffect(stat, Integer.valueOf(0), 0, null, false));
            }
        }
    }
    
    public final int getStolen() {
        return this.stolen;
    }
    
    public final void setStolen(final int s) {
        this.stolen = s;
    }
    
    public final void handleSteal(final MapleCharacter chr) {
        double showdown = 100.0;
        final MonsterStatusEffect mse = this.getBuff(MonsterStatus.SHOWDOWN);
        if (mse != null) {
            showdown += (double)mse.getX().intValue();
        }
        final ISkill steal = SkillFactory.getSkill(4201004);
        final int level = chr.getSkillLevel(steal);
        final int chServerrate = ChannelServer.getInstance(chr.getClient().getChannel()).getDropRate() * MapleParty.活动爆率倍率;
        if (level > 0 && !this.getStats().isBoss() && this.stolen == -1 && steal.getEffect(level).makeChanceResult()) {
            final MapleMonsterInformationProvider mi = MapleMonsterInformationProvider.getInstance();
            final List<MonsterDropEntry> de = new ArrayList(mi.retrieveDrop(this.getId()));
            if (de == null) {
                this.stolen = 0;
                return;
            }
            final List<MonsterDropEntry> dropEntry = new ArrayList((Collection<? extends MonsterDropEntry>)de);
            Collections.shuffle(dropEntry);
            for (final MonsterDropEntry d : dropEntry) {
                if (d.itemId > 0 && d.questid == 0 && d.itemId / 10000 != 238 && Randomizer.nextInt(999999) < (int)((double)(10 * d.chance * chServerrate * chr.getDropMod()) * chr.getDropm() * ((double)chr.getVipExpRate() / 100.0 + 1.0) * (chr.getStat().dropBuff / 100.0) * (showdown / 100.0))) {
                    IItem idrop;
                    if (GameConstants.getInventoryType(d.itemId) == MapleInventoryType.EQUIP) {
                        final Equip eq = (Equip)MapleItemInformationProvider.getInstance().getEquipById(d.itemId);
                        idrop = MapleItemInformationProvider.getInstance().randomizeStats(eq);
                    }
                    else {
                        idrop = new Item(d.itemId, (short)0, (short)((d.Maximum != 1) ? (Randomizer.nextInt(d.Maximum - d.Minimum) + d.Minimum) : 1), (byte)0);
                    }
                    this.stolen = d.itemId;
                    this.map.spawnMobDrop(idrop, this.map.calcDropPos(this.getPosition(), this.getTruePosition()), this, chr, (byte)0, (short)0);
                    break;
                }
            }
        }
        else {
            this.stolen = 0;
        }
    }
    
    public final void setLastNode(final int lastNode) {
        this.lastNode = lastNode;
    }
    
    public final int getLastNode() {
        return this.lastNode;
    }
    
    public final void setLastNodeController(final int lastNode) {
        this.lastNodeController = lastNode;
    }
    
    public final int getLastNodeController() {
        return this.lastNodeController;
    }
    
    public final void cancelDropItem() {
        if (this.dropItemSchedule != null) {
            this.dropItemSchedule.cancel(false);
            this.dropItemSchedule = null;
        }
    }
    
    public final void startDropItemSchedule() {
        this.cancelDropItem();
        if (this.stats.getDropItemPeriod() <= 0 || !this.isAlive()) {
            return;
        }
        final int itemId;
        switch (this.getId()) {
            case 9300061: {
                itemId = 4001101;
                break;
            }
            case 9300102: {
                itemId = 4031507;
                break;
            }
            default: {
                return;
            }
        }
        this.shouldDropItem = false;
        this.dropItemSchedule = MobTimer.getInstance().register((Runnable)new Runnable() {
            @Override
            public void run() {
                if (MapleMonster.this.isAlive() && MapleMonster.this.map != null) {
                    if (MapleMonster.this.shouldDropItem) {
                        MapleMonster.this.map.spawnAutoDrop(itemId, MapleMonster.this.getPosition());
                    }
                    else {
                        MapleMonster.this.shouldDropItem = true;
                    }
                }
            }
        }, (long)(this.stats.getDropItemPeriod() * 1000));
    }
    
    public byte[] getNodePacket() {
        return this.nodepack;
    }
    
    public void setNodePacket(final byte[] np) {
        this.nodepack = np;
    }
    
    public final void killed() {
        if (this.listener != null) {
            this.listener.monsterKilled();
        }
        this.listener = null;
    }
    
    private final class PoisonTask implements Runnable
    {
        private final int poisonDamage;
        private final MapleCharacter chr;
        private final MonsterStatusEffect status;
        private final Runnable cancelTask;
        private final boolean shadowWeb;
        private final MapleMap map;
        
        private PoisonTask(final int poisonDamage, final MapleCharacter chr, final MonsterStatusEffect status, final Runnable cancelTask, final boolean shadowWeb) {
            this.poisonDamage = poisonDamage;
            this.chr = chr;
            this.status = status;
            this.cancelTask = cancelTask;
            this.shadowWeb = shadowWeb;
            this.map = chr.getMap();
        }
        
        @Override
        public void run() {
            long damage = (long)this.poisonDamage;
            if (damage >= MapleMonster.this.hp) {
                damage = MapleMonster.this.hp - 1L;
                if (!this.shadowWeb) {
                    this.cancelTask.run();
                    this.status.cancelTask();
                }
            }
            if (MapleMonster.this.hp > 1L && damage > 0L) {
                MapleMonster.this.damage(this.chr, damage, false);
                if (this.shadowWeb) {
                    this.map.broadcastMessage(MobPacket.damageMonster(MapleMonster.this.getObjectId(), damage), MapleMonster.this.getPosition());
                }
            }
        }
    }
    
    private static class AttackingMapleCharacter
    {
        private final WeakReference<MapleCharacter> attacker;
        private long lastAttackTime;
        
        public AttackingMapleCharacter(final MapleCharacter attacker, final long lastAttackTime) {
            this.attacker = new WeakReference(attacker);
            this.lastAttackTime = lastAttackTime;
        }
        
        public final long getLastAttackTime() {
            return this.lastAttackTime;
        }
        
        public final void setLastAttackTime(final long lastAttackTime) {
            this.lastAttackTime = lastAttackTime;
        }
        
        public final MapleCharacter getAttacker() {
            return (MapleCharacter)this.attacker.get();
        }
    }
    
    private final class SingleAttackerEntry implements AttackerEntry
    {
        private long damage;
        private final int chrid;
        private long lastAttackTime;
        private final int channel;
        
        public SingleAttackerEntry(final MapleCharacter from, final int cserv) {
            this.damage = 0L;
            this.chrid = from.getId();
            this.channel = cserv;
        }
        
        @Override
        public void addDamage(final MapleCharacter from, final long damage, final boolean updateAttackTime) {
            if (this.chrid == from.getId()) {
                this.damage += damage;
                if (updateAttackTime) {
                    this.lastAttackTime = System.currentTimeMillis();
                }
            }
        }
        
        @Override
        public final List<AttackingMapleCharacter> getAttackers() {
            final MapleCharacter chr = MapleMonster.this.map.getCharacterById(this.chrid);
            if (chr != null) {
                return Collections.singletonList(new AttackingMapleCharacter(chr, this.lastAttackTime));
            }
            return Collections.emptyList();
        }
        
        @Override
        public boolean contains(final MapleCharacter chr) {
            return this.chrid == chr.getId();
        }
        
        @Override
        public long getDamage() {
            return this.damage;
        }
        
        @Override
        public void killedMob(final MapleMap map, final int baseExp, final boolean mostDamage, final int lastSkill) {
            final MapleCharacter chr = map.getCharacterById(this.chrid);
            if (chr != null && chr.isAlive()) {
                MapleMonster.this.giveExpToCharacter(chr, baseExp, mostDamage, 1, (byte)0, (byte)0, (byte)0, lastSkill);
            }
        }
        
        @Override
        public int hashCode() {
            return this.chrid;
        }
        
        @Override
        public final boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (this.getClass() != obj.getClass()) {
                return false;
            }
            final SingleAttackerEntry other = (SingleAttackerEntry)obj;
            return this.chrid == other.chrid;
        }
    }
    
    private static final class ExpMap
    {
        public final int exp;
        public final byte ptysize;
        public final byte Class_Bonus_EXP;
        public final byte Premium_Bonus_EXP;
        
        public ExpMap(final int exp, final byte ptysize, final byte Class_Bonus_EXP, final byte Premium_Bonus_EXP) {
            this.exp = exp;
            this.ptysize = ptysize;
            this.Class_Bonus_EXP = Class_Bonus_EXP;
            this.Premium_Bonus_EXP = Premium_Bonus_EXP;
        }
    }
    
    private static final class OnePartyAttacker
    {
        public MapleParty lastKnownParty;
        public long damage;
        public long lastAttackTime;
        
        public OnePartyAttacker(final MapleParty lastKnownParty, final long damage) {
            this.lastKnownParty = lastKnownParty;
            this.damage = damage;
            this.lastAttackTime = System.currentTimeMillis();
        }
    }
    
    private class PartyAttackerEntry implements AttackerEntry
    {
        private long totDamage;
        private final Map<Integer, OnePartyAttacker> attackers;
        private final int partyid;
        private final int channel;
        
        public PartyAttackerEntry(final int partyid, final int cserv) {
            this.attackers = (Map<Integer, OnePartyAttacker>)new HashMap(6);
            this.partyid = partyid;
            this.channel = cserv;
        }
        
        @Override
        public List<AttackingMapleCharacter> getAttackers() {
            final List<AttackingMapleCharacter> ret = (List<AttackingMapleCharacter>)new ArrayList(this.attackers.size());
            for (final Entry<Integer, OnePartyAttacker> entry : this.attackers.entrySet()) {
                final MapleCharacter chr = MapleMonster.this.map.getCharacterById(((Integer)entry.getKey()).intValue());
                if (chr != null) {
                    ret.add(new AttackingMapleCharacter(chr, ((OnePartyAttacker)entry.getValue()).lastAttackTime));
                }
            }
            return ret;
        }
        
        private Map<MapleCharacter, OnePartyAttacker> resolveAttackers() {
            final Map<MapleCharacter, OnePartyAttacker> ret = (Map<MapleCharacter, OnePartyAttacker>)new HashMap(this.attackers.size());
            for (final Entry<Integer, OnePartyAttacker> aentry : this.attackers.entrySet()) {
                final MapleCharacter chr = MapleMonster.this.map.getCharacterById(((Integer)aentry.getKey()).intValue());
                if (chr != null) {
                    ret.put(chr, aentry.getValue());
                }
            }
            return ret;
        }
        
        @Override
        public final boolean contains(final MapleCharacter chr) {
            return this.attackers.containsKey(Integer.valueOf(chr.getId()));
        }
        
        @Override
        public final long getDamage() {
            return this.totDamage;
        }
        
        @Override
        public void addDamage(final MapleCharacter from, final long damage, final boolean updateAttackTime) {
            final OnePartyAttacker oldPartyAttacker = (OnePartyAttacker)this.attackers.get(Integer.valueOf(from.getId()));
            if (oldPartyAttacker != null) {
                final OnePartyAttacker onePartyAttacker2 = oldPartyAttacker;
                onePartyAttacker2.damage += damage;
                oldPartyAttacker.lastKnownParty = from.getParty();
                if (updateAttackTime) {
                    oldPartyAttacker.lastAttackTime = System.currentTimeMillis();
                }
            }
            else {
                final OnePartyAttacker onePartyAttacker = new OnePartyAttacker(from.getParty(), damage);
                this.attackers.put(Integer.valueOf(from.getId()), onePartyAttacker);
                if (!updateAttackTime) {
                    onePartyAttacker.lastAttackTime = 0L;
                }
            }
            this.totDamage += damage;
        }
        
        @Override
        public final void killedMob(final MapleMap map, final int baseExp, final boolean mostDamage, final int lastSkill) {
            MapleCharacter highest = null;
            long highestDamage = 0L;
            final Map<MapleCharacter, ExpMap> expMap = (Map<MapleCharacter, ExpMap>)new HashMap(6);
            byte added_partyinc = 0;
            for (final Entry<MapleCharacter, OnePartyAttacker> attacker : this.resolveAttackers().entrySet()) {
                final MapleParty party = ((OnePartyAttacker)attacker.getValue()).lastKnownParty;
                double averagePartyLevel = 0.0;
                byte Class_Bonus_EXP = 0;
                byte Premium_Bonus_EXP = 0;
                final List<MapleCharacter> expApplicable = (List<MapleCharacter>)new ArrayList();
                for (final MaplePartyCharacter partychar : party.getMembers()) {
                    if (((MapleCharacter)attacker.getKey()).getLevel() - partychar.getLevel() <= 5 || MapleMonster.this.stats.getLevel() - partychar.getLevel() <= 5) {
                        final MapleCharacter pchr = map.getCharacterById(partychar.getId());
                        if (pchr == null || !pchr.isAlive() || pchr.getMap() != map) {
                            continue;
                        }
                        expApplicable.add(pchr);
                        averagePartyLevel += (double)pchr.getLevel();
                        if (Class_Bonus_EXP == 0) {}
                        if (pchr.getStat().equippedWelcomeBackRing && Premium_Bonus_EXP == 0) {
                            Premium_Bonus_EXP = 80;
                        }
                        if (!pchr.getStat().hasPartyBonus || added_partyinc >= 4) {
                            continue;
                        }
                        ++added_partyinc;
                    }
                }
                if (expApplicable.size() > 1) {
                    averagePartyLevel /= (double)expApplicable.size();
                }
                else {
                    Class_Bonus_EXP = 0;
                }
                final long iDamage = ((OnePartyAttacker)attacker.getValue()).damage;
                if (iDamage > highestDamage) {
                    highest = (MapleCharacter)attacker.getKey();
                    highestDamage = iDamage;
                }
                final double innerBaseExp = (double)baseExp * ((double)iDamage / (double)this.totDamage);
                final double expFraction = innerBaseExp / (double)(expApplicable.size() + 1);
                for (final MapleCharacter expReceiver : expApplicable) {
                    int iexp = (expMap.get(expReceiver) == null) ? 0 : ((ExpMap)expMap.get(expReceiver)).exp;
                    final double expWeight = (expReceiver == attacker.getKey()) ? 200.0 : ((double)((Integer)Start.ConfigValuesMap.get("修正队员分配经验")).intValue());
                    double levelMod = (double)expReceiver.getLevel() / averagePartyLevel;
                    if (levelMod > 1.0 || this.attackers.containsKey(Integer.valueOf(expReceiver.getId()))) {
                        levelMod = 1.0;
                    }
                    if (((Integer)Start.ConfigValuesMap.get("越级带人开关")).intValue() > 0) {
                        if (((Integer)Start.ConfigValuesMap.get("越级带人道具开关")).intValue() > 0) {
                            if (((MapleCharacter)attacker.getKey()).getItemQuantity(((Integer)Start.ConfigValuesMap.get("越级带人道具")).intValue(), true) > 0) {
                                levelMod = 1.0;
                            }
                        }
                        else {
                            levelMod = 1.0;
                        }
                    }
                    iexp += (int)Math.round(expFraction * expWeight * levelMod / 100.0);
                    expMap.put(expReceiver, new ExpMap(iexp, (byte)(expApplicable.size() + added_partyinc), Class_Bonus_EXP, Premium_Bonus_EXP));
                }
            }
            for (final Entry<MapleCharacter, ExpMap> expReceiver2 : expMap.entrySet()) {
                final ExpMap expmap = (ExpMap)expReceiver2.getValue();
                MapleMonster.this.giveExpToCharacter((MapleCharacter)expReceiver2.getKey(), expmap.exp, mostDamage && expReceiver2.getKey() == highest, expMap.size(), expmap.ptysize, expmap.Class_Bonus_EXP, expmap.Premium_Bonus_EXP, lastSkill);
            }
        }
        
        @Override
        public final int hashCode() {
            final int prime = 31;
            int result = 1;
            result = 31 * result + this.partyid;
            return result;
        }
        
        @Override
        public final boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (this.getClass() != obj.getClass()) {
                return false;
            }
            final PartyAttackerEntry other = (PartyAttackerEntry)obj;
            return this.partyid == other.partyid;
        }
    }
    
    private interface AttackerEntry
    {
        List<AttackingMapleCharacter> getAttackers();
        
        void addDamage(final MapleCharacter p0, final long p1, final boolean p2);
        
        long getDamage();
        
        boolean contains(final MapleCharacter p0);
        
        void killedMob(final MapleMap p0, final int p1, final boolean p2, final int p3);
    }
}
