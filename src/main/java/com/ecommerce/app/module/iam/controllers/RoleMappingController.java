package com.ecommerce.app.module.iam.controllers;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.ecommerce.app.module.iam.models.*;
import com.ecommerce.app.module.iam.security.RequiresRole;
import com.ecommerce.app.module.iam.services.MapperService;
import com.ecommerce.app.module.iam.util.SendResponseUtil;
import java.util.*;
import java.io.IOException;

@WebServlet("/map")
@RequiresRole("Admin")
public class RoleMappingController extends HttpServlet {
    MapperService service = new MapperService();
        public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            if(request.getParameter("userId")!=null){
                String userID = request.getParameter("userId");
                String roleRaw = request.getParameter("roleId");
                if(roleRaw == null){
                    SendResponseUtil.sendResponse(new ApiResponse(false, "roleId is required", null, 400), response);
                    return;
                }
                Long roleId;
                try {
                    roleId = Long.parseLong(roleRaw);
                } catch (NumberFormatException nfe) {
                    SendResponseUtil.sendResponse(new ApiResponse(false, "invalid roleId", null, 400), response);
                    return;
                }
               boolean success =  service.mapRoleAndUser(userID, roleId);
                SendResponseUtil.sendResponse(new ApiResponse(success, "user mapped with the role", null, 200), response);
            }else{
                String roleRaw = request.getParameter("roleId");
                String permRaw = request.getParameter("permissionId");
                if(roleRaw == null || permRaw == null){
                    SendResponseUtil.sendResponse(new ApiResponse(false, "roleId and permissionId are required", null, 400), response);
                    return;
                }
                Long roleId;
                Long permissionId;
                try {
                    roleId = Long.parseLong(roleRaw);
                    permissionId = Long.parseLong(permRaw);
                } catch (NumberFormatException nfe) {
                    SendResponseUtil.sendResponse(new ApiResponse(false, "invalid roleId or permissionId", null, 400), response);
                    return;
                }
                boolean success =  service.mapRoleAndPermission(roleId,permissionId);
                SendResponseUtil.sendResponse(new ApiResponse(success, "permission mapped with the role", null, 200), response);
            }
        
        }

                public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            if(request.getParameter("userId")!=null){
                String userID = request.getParameter("userId");
               List<Role> roles =  service.getRolesByUserId(userID);
                SendResponseUtil.sendResponse(new ApiResponse(true, "user mapped  roles", roles, 200), response);
            }else{
                String raw = request.getParameter("roleId");
                if(raw == null){
                    SendResponseUtil.sendResponse(new ApiResponse(false, "roleId is required", null, 400), response);
                    return;
                }
                Long roleId;
                try {
                    roleId = Long.parseLong(raw);
                } catch (NumberFormatException nfe) {
                    SendResponseUtil.sendResponse(new ApiResponse(false, "invalid roleId", null, 400), response);
                    return;
                }
                List<Permission> permissions =  service.getPermissionsbyRoleId(roleId);
                SendResponseUtil.sendResponse(new ApiResponse(true, "permission mapped  role", permissions, 200), response);
            }
        
        }


    }
