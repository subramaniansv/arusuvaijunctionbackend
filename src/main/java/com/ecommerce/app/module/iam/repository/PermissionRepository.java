package com.ecommerce.app.module.iam.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;
import java.util.*;
import com.ecommerce.app.module.iam.config.DBConfig;
import com.ecommerce.app.module.iam.models.Action;
import com.ecommerce.app.module.iam.models.Permission;

public class PermissionRepository {
    private static final Logger LOG = LoggerFactory.getLogger(PermissionRepository.class);

    public Permission create(Permission permission) {
        String sql = "insert into permission (name,description,resource,action) values(?,?,?,?)";
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, permission.getName());
            ps.setString(2, permission.getDescription());
            ps.setString(3, permission.getResource());
            ps.setString(4, permission.getAction().name());
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                permission.setId(rs.getLong(1));
            }
        } catch (SQLException e) {
            LOG.error("Sql exception at create permission iam  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at  create permission iam  ", e);
        }
        return permission;
    }

    public Permission getpermission(Long id) {
        String sql = "select * from  permission where permission_id = ?";
        Permission permission = new Permission();
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                permission.setId(rs.getLong("permission_id"));
                permission.setName(rs.getString("name"));
                permission.setDescription(rs.getString("description"));
                permission.setResource(rs.getString("resource"));
                permission.setAction(Action.valueOf(rs.getString("action")));
            }
        } catch (SQLException e) {
            LOG.error("Sql exception at get permission iam  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at  get permission iam  ", e);
        }

        return permission;
    }

    public List<Permission> getpermissions() {
        String sql = "select * from  permission ";
        List<Permission> permissions = new ArrayList<>();
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
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

    public void deletepermission(Long id){
        String sql = "delete from permission where permission_id =?";
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Sql exception at deletepermission iam  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at  deletepermission iam  ", e);
        }
    }
}
