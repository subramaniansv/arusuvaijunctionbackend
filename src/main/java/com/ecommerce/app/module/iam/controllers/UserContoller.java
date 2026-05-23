package com.ecommerce.app.module.iam.controllers;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.ecommerce.app.module.iam.security.RequiresRole;
import com.ecommerce.app.module.iam.services.UserService;
import com.ecommerce.app.module.iam.util.SendResponseUtil;
import com.ecommerce.app.module.iam.mapper.PassWordResetConverterUtil;
import com.ecommerce.app.module.iam.models.*;
import java.io.*;
/**
 * Admin user management.
 *
 *   POST /api/user                              -> change a user's password (caller supplies old + new)
 *   GET  /api/user                              -> list all users
 *   GET  /api/user?userId=<uuid>                -> fetch one user
 *   PUT  /api/user?userId=<uuid>&status=ACTIVE  -> activate/suspend a user
 *
 * Self-service callers (regular users reading or changing their OWN account)
 * MUST use /api/me - this controller is admin-only.
 */
@WebServlet("/api/user")
@RequiresRole("Admin")
public class UserContoller  extends HttpServlet{
    UserService service = new UserService();

    protected void doPost (HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        PasswordResetRequest ps = PassWordResetConverterUtil.requestToDto(request);
        boolean ok = service.updatePassword(ps);
        SendResponseUtil.sendResponse(new ApiResponse(ok, "password reset", null, 200), response);
    }


    protected void doGet (HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if(request.getParameter("userId") != null){
            User user = service.getUser(request.getParameter("userId"));
            SendResponseUtil.sendResponse(new ApiResponse(true, "user fetched ", user, 200), response);
        }
        else{
             SendResponseUtil.sendResponse(new ApiResponse(true, "user fetched ", service.getAllUsers(), 200), response);
        }
    }

    /**
     * Admin: activate or suspend a user.
     * PUT /api/user?userId=<uuid>&status=<ACTIVE|SUSPENDED|...>
     *
     * Validates the userId is a UUID and the status matches the UserStatus
     * enum so an invalid status returns 400 instead of crashing with 500.
     */
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        String userIdParam = request.getParameter("userId");
        String statusParam = request.getParameter("status");
        if (userIdParam == null || userIdParam.isBlank()
                || statusParam == null || statusParam.isBlank()) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "userId and status are required", null, 400), response);
            return;
        }
        try {
            java.util.UUID.fromString(userIdParam);
        } catch (IllegalArgumentException ex) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "invalid userId", null, 400), response);
            return;
        }
        try {
            UserStatus.valueOf(statusParam.toUpperCase());
        } catch (IllegalArgumentException ex) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "invalid status; allowed: "
                            + java.util.Arrays.toString(UserStatus.values()), null, 400), response);
            return;
        }
        boolean ok = service.updateStatus(userIdParam, statusParam.toUpperCase());
        if (!ok) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "user not found or status unchanged", null, 404), response);
            return;
        }
        SendResponseUtil.sendResponse(
                new ApiResponse(true, "user status updated", null, 200), response);
    }

}
