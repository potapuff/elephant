package edu.sumdu.tss.elephant.helper;

import io.javalin.core.security.RouteRole;
import lombok.Getter;
import org.apache.commons.io.FileUtils;

public enum UserRole implements RouteRole {
    ANYONE(0) {
        public long maxConnections() {
            return 0;
        }

        public long maxDB() {
            return 0;
        }

        public long maxStorage() {
            return 0;
        }

        public long maxBackupsPerDB() {
            return 0;
        }

        @Override
        public long maxScriptsPerDB() {
            return 0;
        }
    },
    UNCHEKED(1) {
        public long maxConnections() {
            return 0;
        }

        public long maxDB() {
            return 0;
        }

        public long maxStorage() {
            return 0;
        }

        public long maxBackupsPerDB() {
            return 0;
        }

        @Override
        public long maxScriptsPerDB() {
            return 0;
        }
    },

    BASIC_USER(2) {
        public long maxConnections() {
            return 5;
        }

        public long maxDB() {
            return 2;
        }

        public long maxStorage() {
            return 20 * FileUtils.ONE_MB;
        }

        public long maxBackupsPerDB() {
            return 1;
        }
        public long maxScriptsPerDB() {
            return 2;
        }
    },
    PROMOTED_USER(3) {
        public long maxConnections() {
            return 5;
        }

        public long maxDB() {
            return 3;
        }

        public long maxStorage() {
            return 50 * FileUtils.ONE_MB;
        }

        public long maxBackupsPerDB() {
            return 5;
        }

        public long maxScriptsPerDB() {
            return 5;
        }
    },
    ADMIN(4) {
        public long maxConnections() {
            return 5;
        }

        public long maxDB() {
            return 100;
        }

        public long maxStorage() {
            return 50 * FileUtils.ONE_MB;
        }

        public long maxBackupsPerDB() {
            return 10;
        }

        public long maxScriptsPerDB() {
            return 10;
        }
    };


    public final static RouteRole[] AUTHED = {UserRole.UNCHEKED, UserRole.BASIC_USER, UserRole.PROMOTED_USER, UserRole.ADMIN};

    @Getter
    private final Long value;

    UserRole(int value) {
        this.value = (long) value;
    }

    public static UserRole byValue(long value) {
        for (UserRole role : UserRole.values()) {
            if (role.value == value) {
                return role;
            }
        }
        throw new RuntimeException("UserRole not found for" + value);
    }

    public abstract long maxConnections();

    public abstract long maxDB();

    public abstract long maxStorage();

    public abstract long maxBackupsPerDB();

    public abstract long maxScriptsPerDB();
}
