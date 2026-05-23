package com.ecommerce.app.module.iam.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;
import java.util.*;
import com.ecommerce.app.module.iam.config.DBConfig;
import com.ecommerce.app.module.iam.models.Role;

public class RoleRepository {
    private static final Logger LOG = LoggerFactory.getLogger(RoleRepository.class);

    public Role create(Role role) {
        String sql = "insert into role (name,description) values(?,?)";
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, role.getName());
            ps.setString(2, role.getDescription());
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                role.setId(rs.getLong(1));
            }
        } catch (SQLException e) {
            LOG.error("Sql exception at create role iam  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at  create role iam  ", e);
        }
        return role;
    }

    public Role getRole(Long id) {
        String sql = "select * from  role where role_id = ?";
        Role role = new Role();
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                role.setId(rs.getLong("role_id"));
                role.setName(rs.getString("name"));
                role.setDescription(rs.getString("description"));
            }
        } catch (SQLException e) {
            LOG.error("Sql exception at get role iam  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at  get role iam  ", e);
        }

        return role;
    }

    public List<Role> getRoles() {
        String sql = "select * from  role ";
        List<Role> roles = new ArrayList<>();
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Role role = new Role();
                role.setId(rs.getLong("role_id"));
                role.setName(rs.getString("name"));
                role.setDescription(rs.getString("description"));
                roles.add(role);
            }
        } catch (SQLException e) {
            LOG.error("Sql exception at get role iam  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at  get role iam  ", e);
        }
        return roles;

    }

    public void deleteRole(Long id){
        String sql = "delete from role where role_id =?";
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Sql exception at deleteRole iam  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at  deleteRole iam  ", e);
        }
    }
}
