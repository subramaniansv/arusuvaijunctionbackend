package com.ecommerce.app.module.iam.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;
import java.util.*;

import com.ecommerce.app.module.iam.config.DBConfig;
import com.ecommerce.app.module.iam.models.*;

public class MapperRepository {
    private static final Logger LOG = LoggerFactory.getLogger(MapperRepository.class);

    public boolean mapRoleAndUser(UUID userId, Long roleId) {
        String sql = "insert into user_role (user_id,role_id) values(?,?)";
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setObject(1, userId);
            ps.setLong(2, roleId);
             return ps.executeUpdate() >0;
        } catch (SQLException e) {
            LOG.error("Sql exception at mapRoleAndUser iam  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at  mapRoleAndUser iam  ", e);
        }

        return false;
    }

    public boolean mapRoleAndPermission(Long roleId, Long permissionId) {
        String sql = "insert into role_permission (role_id,permission_id) values(?,?)";
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setLong(1, roleId);
            ps.setLong(2, permissionId);
            return ps.executeUpdate() >0;
        } catch (SQLException e) {
            LOG.error("Sql exception at mapRoleAndPermission iam  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at  mapRoleAndPermission iam  ", e);
        }

        return false;
    }

    public List<Permission> getPermissionsbyRoleId(Long roleId) {
        String sql = "select * from permission p join role_permission r on r.permission_id = p.permission_id where r.role_id = ? ";
        List<Permission> permissions = new ArrayList<>();
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setLong(1, roleId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Permission permission = new Permission();
                permission.setId(rs.getLong("permission_id"));
                permission.setName(rs.getString("name"));
                permission.setDescription(rs.getString("description"));
                permission.setResource(rs.getString("resource"));
                permission.setAction(Action.valueOf(rs.getString("action")));
                permissions.add(permission);
            }
        } catch (SQLException e) {
            LOG.error("Sql exception at get permission iam  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at  get permission iam  ", e);
        }
        return permissions;
    }

    public List<Role> getRolesAndPermissionsByUserId(UUID userId) {

        String sql = """
                SELECT
                    r.role_id       AS role_id,
                    r.name          AS role_name,
                    r.description   AS role_desc,
                    p.permission_id AS permission_id,
                    p.name          AS permission_name,
                    p.description   AS permission_desc,
                    p.resource      AS resource,
                    p.action        AS action
                FROM role r
                JOIN user_role ur         ON r.role_id       = ur.role_id
                LEFT JOIN role_permission rp ON r.role_id    = rp.role_id
                LEFT JOIN permission p    ON rp.permission_id = p.permission_id
                WHERE ur.user_id = ?
                """;

        Map<Long, Role> roleMap = new LinkedHashMap<>();

        try (Connection connection = DBConfig.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setObject(1, userId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Long roleId = rs.getLong("role_id");

                Role role = roleMap.computeIfAbsent(roleId, id -> {
                    Role r = new Role();
                    try {
                        r.setId(id);
                        r.setName(rs.getString("role_name"));
                        r.setDescription(rs.getString("role_desc"));
                        r.setPermissions(new ArrayList<>());
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return r;
                });

                long permissionId = rs.getLong("permission_id");
                if (!rs.wasNull()) {
                    Permission permission = new Permission();
                    permission.setId(permissionId);
                    permission.setName(rs.getString("permission_name"));
                    permission.setDescription(rs.getString("permission_desc"));
                    permission.setResource(rs.getString("resource"));
                    permission.setAction(Action.valueOf(rs.getString("action")));
                    role.getPermissions().add(permission);
                }
            }

        } catch (SQLException e) {
            LOG.error("SQL exception at getRolesAndPermissionsByUserId:  ", e);
        } catch (Exception e) {
            LOG.error("Unhandled exception at getRolesAndPermissionsByUserId:  ", e);
        }

        return new ArrayList<>(roleMap.values());
    }


}
