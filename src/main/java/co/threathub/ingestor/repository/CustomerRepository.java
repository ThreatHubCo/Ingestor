package co.threathub.ingestor.repository;

import com.zaxxer.hikari.HikariDataSource;
import co.threathub.ingestor.repository.exception.DatabaseException;
import co.threathub.ingestor.model.Customer;
import lombok.RequiredArgsConstructor;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class CustomerRepository {
    private final HikariDataSource dataSource;

    /**
     * Returns all customers that are enabled and have a valid tenant id.
     */
    public List<Customer> getAllCustomers() {
        List<Customer> list = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM customers WHERE deleted_at IS NULL AND tenant_id IS NOT NULL");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(Customer.of(rs));
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to fetch customers", ex);
        }
        return list;
    }

    /**
     * Returns a single customer by ID if they are enabled and have a valid tenant id.
     * If they are disabled, return null.

     * @param id The customer ID to lookup
     * @return The customer information
     */
    public Customer getCustomerById(int id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM customers WHERE id = ? AND deleted_at IS NULL AND tenant_id IS NOT NULL")) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Customer.of(rs);
                }
            }
            return null;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to fetch customer with ID " + id, ex);
        }
    }
}
