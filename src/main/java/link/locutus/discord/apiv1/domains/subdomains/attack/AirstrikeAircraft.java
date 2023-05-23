package link.locutus.discord.apiv1.domains.subdomains.attack;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;

public abstract class AirstrikeAircraft extends AbstractAttack{
    protected AirstrikeAircraft(int id, long date, boolean isAttackerIdGreater) {
        super(id, date, isAttackerIdGreater);
    }

    public static AirstrikeAircraft create(int id, long date, boolean isAttackerIdGreater,
                                           SuccessType success,
                                           int attcas1,
                                           int defcas1,
                                           double city_infra_before,
                                           double infra_destroyed,
                                           int improvements_destroyed,
                                           double att_gas_used,
                                           double att_mun_used,
                                           double def_gas_used,
                                           double def_mun_used) {
        if (improvements_destroyed == 0) {
            if (success == SuccessType.IMMENSE_TRIUMPH && defcas1 == 0) {
                return new AirstrikeAircraftIt_0_NoImp(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed, att_gas_used, att_mun_used);
            }
            return new AirstrikeAircraftAny_Any_NoImp(id, date, isAttackerIdGreater, success, attcas1, defcas1, city_infra_before, infra_destroyed, att_gas_used, att_mun_used, def_gas_used, def_mun_used);
        } else {
            if (success == SuccessType.IMMENSE_TRIUMPH && defcas1 == 0) {
                return new AirstrikeAircraftIt_0_Imp(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed, att_gas_used, att_mun_used);
            }
            return new AirstrikeAircraftAny_Any_Imp(id, date, isAttackerIdGreater, success, attcas1, defcas1, city_infra_before, infra_destroyed, att_gas_used, att_mun_used, def_gas_used, def_mun_used);
        }
    }

    public static class AirstrikeAircraftIt_0_NoImp extends AirstrikeAircraft {
        private final long data;

        public AirstrikeAircraftIt_0_NoImp(int id, long date, boolean isAttackerIdGreater, double cityInfraBefore, double infraDestroyed, double att_gas_used, double att_mun_used) {
            super(id, date, isAttackerIdGreater);
            // city_infra_before = 15
            // infra_destroyed = 15
            // att_gas_used = 17
            // att_mun_used = 17
            this.data = (long) cityInfraBefore << 49 | (long) infraDestroyed << 34 | (long) att_gas_used << 17 | (long) att_mun_used;
        }

        @Override
        public double getCity_infra_before() {
            return (data >> 49) & 0x7FFF;
        }

        @Override
        public SuccessType getSuccess() {
            return SuccessType.IMMENSE_TRIUMPH;
        }

        @Override
        public int getAttcas1() {
            return 0;
        }

        @Override
        public int getDefcas1() {
            return 0;
        }

        @Override
        public double getInfra_destroyed() {
            return (data >> 34) & 0x7FFF;
        }

        @Override
        public int getImprovements_destroyed() {
            return 0;
        }

        @Override
        public double getAtt_gas_used() {
            return (data >> 17) & 0x1FFFF;
        }

        @Override
        public double getAtt_mun_used() {
            return data & 0x1FFFF;
        }

        @Override
        public double getDef_gas_used() {
            return 0;
        }

        @Override
        public double getDef_mun_used() {
            return 0;
        }
    }

    public static class AirstrikeAircraftIt_0_Imp extends AirstrikeAircraftIt_0_NoImp {
        public AirstrikeAircraftIt_0_Imp(int id, long date, boolean isAttackerIdGreater, double cityInfraBefore, double infraDestroyed, double att_gas_used, double att_mun_used) {
            super(id, date, isAttackerIdGreater, cityInfraBefore, infraDestroyed, att_gas_used, att_mun_used);
        }

        @Override
        public int getImprovements_destroyed() {
            return 1;
        }
    }

    public static class AirstrikeAircraftAny_Any_NoImp extends AirstrikeAircraft {
        private final long data;
        private final long data2;

        public AirstrikeAircraftAny_Any_NoImp(int id, long date, boolean isAttackerIdGreater, SuccessType success,
                                     int attcas1, int defcas1,
                                     double city_infra_before, double infra_destroyed,
                                     double att_gas_used, double att_mun_used, double def_gas_used, double def_mun_used) {
            super(id, date, isAttackerIdGreater);
            this.data = (long) success.ordinal() << 62 | (long) attcas1 << 49 | (long) defcas1 << 36 | (long) att_gas_used << 18 | (long) att_mun_used;
            this.data2 = (long) def_gas_used << 46 | (long) def_mun_used << 28 | (long) city_infra_before << 14 | (long) infra_destroyed;
        }

        @Override
        public SuccessType getSuccess() {
            return SuccessType.values()[(int) (data >> 62) & 0x3];
        }

        @Override
        public int getAttcas1() {
            return (int) (data >> 49) & 0x7FFF;
        }

        @Override
        public int getDefcas1() {
            return (int) (data >> 36) & 0x1FFF;
        }

        @Override
        public double getAtt_gas_used() {
            return (data >> 18) & 0x3FFFF;
        }

        @Override
        public double getAtt_mun_used() {
            return data & 0x3FFFF;
        }

        @Override
        public double getDef_gas_used() {
            return (data2 >> 46) & 0x3FFFF;
        }

        @Override
        public double getDef_mun_used() {
            return (data2 >> 28) & 0x3FFFF;
        }

        @Override
        public double getCity_infra_before() {
            return (data2 >> 14) & 0x3FFF;
        }

        @Override
        public double getInfra_destroyed() {
            return data2 & 0x3FFF;
        }

        @Override
        public int getImprovements_destroyed() {
            return 0;
        }
    }

    public static class AirstrikeAircraftAny_Any_Imp extends AirstrikeAircraftAny_Any_NoImp {

        public AirstrikeAircraftAny_Any_Imp(int id, long date, boolean isAttackerIdGreater, SuccessType success, int attcas1, int defcas1, double city_infra_before, double infra_destroyed, double att_gas_used, double att_mun_used, double def_gas_used, double def_mun_used) {
            super(id, date, isAttackerIdGreater, success, attcas1, defcas1, city_infra_before, infra_destroyed, att_gas_used, att_mun_used, def_gas_used, def_mun_used);
        }

        @Override
        public int getImprovements_destroyed() {
            return 1;
        }
    }

    @Override
    public AttackType getAttack_type() {
        return AttackType.AIRSTRIKE_AIRCRAFT;
    }

    @Override
    public int getAttcas2() {
        return 0;
    }

    @Override
    public int getDefcas2() {
        return 0;
    }

    @Override
    public int getDefcas3() {
        return 0;
    }

    @Override
    public double getMoney_looted() {
        return 0;
    }

    @Override
    public double[] getLoot() {
        return null;
    }

    @Override
    public double getLootPercent() {
        return 0;
    }
}
