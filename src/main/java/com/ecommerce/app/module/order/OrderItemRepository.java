package com.ecommerce.app.module.order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;
import java.util.*;

import com.ecommerce.app.config.DBConfig;
public class OrderItemRepository {
    private static final Logger LOG = LoggerFactory.getLogger(OrderItemRepository.class);

    public OrderItem create(Connection connection,OrderItem orderItem){
        // Explicit column list - the table now has variant_id / variant_label
        // (added after this code was first written) and positional INSERT
        // would silently shift values into the wrong columns.
        String sql = "INSERT INTO order_items "
                + "(order_item_id, order_id, product_id, quantity, price, variant_id, variant_label) "
                + "VALUES (?,?,?,?,?,?,?)";
        try  {
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            UUID id =UUID.randomUUID();
            preparedStatement.setObject(1, id);
            preparedStatement.setObject(2, orderItem.getOrderId());
            preparedStatement.setObject(3, orderItem.getProductId());
            preparedStatement.setInt(4, orderItem.getQuantity());
            preparedStatement.setDouble(5, orderItem.getPrice());
            preparedStatement.setObject(6, orderItem.getVariantId());
            preparedStatement.setString(7, orderItem.getVariantLabel());
            preparedStatement.executeUpdate();
            orderItem.setOrderItemId(id);
        } catch (SQLException e) {
            LOG.error("sql exception at create order item ", e);
        }catch(Exception e){
             LOG.error("unhandled exception at create order item ", e);
        }
        return orderItem;
    }

    public List<OrderItem> findByOrderId(UUID orderId){
        String sql ="select * from order_items where order_id =?";
        List<OrderItem> items = new ArrayList<>();
        try (Connection connection =  DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setObject(1, orderId);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                OrderItem item = new OrderItem();
                item.setOrderItemId(rs.getObject("order_item_id", java.util.UUID.class));
                item.setOrderId(rs.getObject("order_id", java.util.UUID.class));
                item.setProductId(rs.getObject("product_id", java.util.UUID.class));
                item.setVariantId(rs.getObject("variant_id", java.util.UUID.class));
                item.setVariantLabel(rs.getString("variant_label"));
                item.setQuantity(rs.getInt("quantity"));
                item.setPrice(rs.getDouble("price"));

                items.add(item);
            }
        } catch (SQLException e) {
            LOG.error("sql exception at findByOrderId order item ", e);
        }catch(Exception e){
             LOG.error("unhandled exception at findByOrderId order item ", e);
        }

        return items;
    }

    boolean deleteByOrderId(Connection connection,UUID orderId){
        String sql = "delete from order_items where order_id =?";
        try  {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setObject(1, orderId);
           return ps.executeUpdate()>0;
        }  catch (SQLException e) {
            LOG.error("sql exception at deleteByOrderId order item ", e);
        }catch(Exception e){
             LOG.error("unhandled exception at deleteByOrderId order item ", e);
        }
        return false;
    }
}
