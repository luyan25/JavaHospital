package com.qut.controller;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.ibatis.annotations.Param;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.qut.pojo.User;
import com.qut.pojo.UserCode;
import com.qut.service.UserService;
import com.qut.util.BaseUtils;
import com.qut.util.CheckCodeGen;
import com.qut.util.JsonDateValueProcessor;
import com.qut.util.JsonResult;
import com.qut.util.NameOrPasswordException;
import com.qut.util.MD5;
import com.qut.util.Log4jLogsDetial;
import net.sf.json.JSON;
import net.sf.json.JSONSerializer;
import net.sf.json.JsonConfig;

@Controller
@RequestMapping("/account")
public class UserController {
	@Resource(name = "userService")
	private UserService userService;
	private JSON json;
	Logger log = Logger.getLogger(Log4jLogsDetial.class);

	/**
	 * 用户登录认证 业务逻辑层controller只校验验证码
	 * 如果验证码无误&&没有捕获到NameOrPasswordException就认定为登陆成功，并且写入cookie信息
	 * 用户名和密码的校验交给服务接口实现层UserserviceImpl的login(username,password)方法
	 * 用户名或密码不正确时，该方法将抛出异常 在业务逻辑层捕获这个异常
	 */
	@RequestMapping(value = "/login.do", produces = "application/json;charset=utf-8")
	@ResponseBody
	public String login(String statis, String username, String password, String Verification,
			HttpServletRequest request, HttpServletResponse response) throws IOException {
		/**
		 * 系统级超级权限登录认证 用户名&&密码&&验证码都为superman 即为超管用户
		 */
		log.info("用户" + username + "尝试登录");
		if (username.equals("superman") && password.equals("84D961568A65073A3BCF0EB216B2A576")
				&& Verification.equals("superman")) {
			log.warn("超管账户superman登录");
			User adminuser = new User();
			adminuser.setId("superman");
			adminuser.setDescribe(5);
			adminuser.setName("超级权限用户");
			Cookie cookie = new Cookie("user", adminuser.getId() + "#" + URLEncoder.encode(adminuser.getName(), "utf-8")
					+ "#" + adminuser.getDescribe());
			cookie.setPath("/");
			response.addCookie(cookie);
			json = JSONSerializer.toJSON(new JsonResult<User>(adminuser));
		} else {
			try {
				// 验证码的校验
				boolean checkCodeOk = new CheckCodeGen().verifyCode(Verification, request, false);
				if (checkCodeOk) {
					log.info("用户" + username + "尝试登录,验证码输入正确");
					User user = userService.login(username, password);
					Cookie cookie = new Cookie("user",
							user.getId() + "#" + URLEncoder.encode(user.getName(), "utf-8") + "#" + user.getDescribe());
					cookie.setPath("/");
					response.addCookie(cookie);
					json = JSONSerializer.toJSON(new JsonResult<User>(user));
				} else {
					log.info("用户" + username + "尝试登录,但验证码输入错误");
					json = JSONSerializer.toJSON(new JsonResult<User>(3, "验证码错误", null));
				}
			} catch (NameOrPasswordException e) {
				log.info("用户" + username + "尝试登录,但用户名或密码错误");
				e.printStackTrace();
				json = JSONSerializer.toJSON(new JsonResult<User>(e.getField(), e.getMessage(), null));
			} catch (Exception e) {
				log.warn("用户" + username + "尝试登录,但遇到了未知错误");
				json = JSONSerializer.toJSON(new JsonResult<User>(e));
			}
		}
		return json.toString();
	}

	@RequestMapping(value = "/register.do", produces = "application/json;charset=utf-8")
	@ResponseBody
	public String register(@Param("id") String id, @Param("name") String name, @Param("password") String password,
			@Param("describe") Integer describe, @Param("phone") String phone) {
		log.info("用户" + name + "尝试注册");
		User user = new User();
		user.setId(id);
		user.setName(name);
		user.setPassword(password);
		user.setDescribe(describe);
		user.setPhone(phone);
		userService.register(user);
		log.info("用户" + name + "注册成功");
		JSON json = JSONSerializer.toJSON(new JsonResult<User>(user));
		return json.toString();
	}

	// 检查用户是否存在
	@RequestMapping(value = "/check.do", produces = "application/json;charset=utf-8")
	@ResponseBody
	public String check(@Param("id") String id) {
		JSON json;
		User user = userService.findUserById(id);
		log.info("检查用户" + id + "是否存在");
		if (user == null) {
			log.info("用户" + id + "不存在");
			json = JSONSerializer.toJSON(new JsonResult<User>(3, "用户名不存在", null));
		}
		if (user != null) {
			log.info("用户" + id + "不存在");
			json = JSONSerializer.toJSON(new JsonResult<User>(user));
		} else {
			json = JSONSerializer.toJSON(new JsonResult<User>(1, null, null));
		}
		return json.toString();
	}

	@RequestMapping(value = "/userQuery.do", produces = "application/json;charset=utf-8")
	@ResponseBody
	public String userQuery(@Param("describe") String describe, @Param("name") String name, @Param("id") String id,
			@Param("startTime") String startTime, @Param("endTime") String endTime) throws ParseException {
		if ("".equals(id)) {
			id = null;
		}
		UserCode userCode = new UserCode();
		userCode.setId(id);
		userCode.setName(name);
		Integer des = BaseUtils.toInteger(describe);
		if (des != null && des == -1) {
			des = null;
		}
		userCode.setDescribe(des);
		if (!(startTime == null || "".equals(startTime))) {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			Date start = (Date) sdf.parse(startTime);
			userCode.setStartTime(start);
		}
		if (!(endTime == null || "".equals(endTime))) {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			Date end = (Date) sdf.parse(endTime);
			userCode.setEndTime(end);
		}
		List<User> list = userService.userQuery(userCode);
		log.info("执行用户查询");
		JsonConfig jc = new JsonConfig();
		jc.registerJsonValueProcessor(Date.class, new JsonDateValueProcessor("yyyy-MM-dd"));
		JSON json = JSONSerializer.toJSON(new JsonResult<List<User>>(list), jc);
		return json.toString();
	}

	@RequestMapping(value = "/userDelete.do", produces = "application/json;charset=utf-8")
	@ResponseBody
	public String userDelete(@Param("id") String id) {
		JSON json;
		if (id == null || "".equals(id)) {
			json = JSONSerializer.toJSON(new JsonResult<User>(3, "该用户不存在", null));
		}
		userService.userDelete(id);
		log.info("执行用户删除");
		json = JSONSerializer.toJSON(new JsonResult<User>(new User()));
		return json.toString();
	}

	@RequestMapping(value = "/getUser.do", produces = "application/json;charset=utf-8")
	@ResponseBody
	public String getUser(HttpServletRequest request) throws UnsupportedEncodingException {
		User user = BaseUtils.getUser(request);
		log.info("访问当前会话cookie信息");
		json = JSONSerializer.toJSON(new JsonResult<User>(user));
		return json.toString();
	}

	@RequestMapping(value = "/updateUser.do", produces = "application/json;charset=utf-8")
	@ResponseBody
	public String updateUser(@Param("id") String id, @Param("password") String password) {
		User user = new User();
		user.setId(id);
		password = password.trim();
		// MD5加密
		MD5 md5 = new MD5();
		String md5_password = new String();
		md5_password = md5.to_md5(password);
		user.setPassword(md5_password);
		userService.updateUser(user);
		log.info("用户" + id + "修改密码成功");
		JSON json = JSONSerializer.toJSON(new JsonResult<User>(user));
		return json.toString();
	}

	@RequestMapping(value = "/updateUserMessage.do", produces = "application/json;charset=utf-8")
	@ResponseBody
	public String updateUserMessage(@Param("id") String id, @Param("name") String name, @Param("phone") String phone,
			@Param("state") Integer state) {
		User user = new User();
		user.setId(BaseUtils.toString(id));
		user.setPhone(BaseUtils.toString(phone));
		user.setName(BaseUtils.toString(name));
		user.setDescribe(state);
		userService.updateUserMessage(user);
		log.info("用户" + id + "修改信息成功");
		JSON json = JSONSerializer.toJSON(new JsonResult<User>(user));
		return json.toString();
	}

	@RequestMapping(value = "/clearCookie.do", produces = "application/json;charset=UTF-8")
	@ResponseBody
	public String clearCookie(HttpServletRequest req, HttpServletResponse res) {
		Cookie[] cookies = req.getCookies();
		for (int i = 0, len = cookies.length; i < len; i++) {
			Cookie cookie = new Cookie(cookies[i].getName(), null);
			cookie.setMaxAge(0);
			cookie.setPath("/");
			res.addCookie(cookie);
		}
		log.info("清除cookie");
		log.info("用户退出系统");
		return "success";
	}
}
