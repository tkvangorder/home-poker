package org.homepoker.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * A simple filter for extracting JWT tokens from incoming requests and validating them. There is no need (at this time)
 * for anything more complicated like an openID authorization server/resource server setup. This filtered is registered
 * in the WebSecurityConfig class as part of the security filter chain.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtTokenService jwtTokenService;
  private final UserDetailsService userDetailsService;

  public JwtAuthenticationFilter(JwtTokenService jwtTokenService, UserDetailsService userDetailsService) {
    this.jwtTokenService = jwtTokenService;
    this.userDetailsService = userDetailsService;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
    String authorizationHeader = request.getHeader("Authorization");
    if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
      String jwtToken = authorizationHeader.substring(7);
      // Extract the user login id from the token, this will fail with an exception if the token is expired.
      String userLoginId = jwtTokenService.extractUserLoginId(jwtToken);
      if (userLoginId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
        // If we get to this point, the token is valid and we have not previously authenticated the user.
        // Load the user details from the userDetailsService and set the security context with the current user.
        UserDetails userDetails = userDetailsService.loadUserByUsername(userLoginId);
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
            userDetails, null, userDetails.getAuthorities());
        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
      }
    }
    // Must always call the rest of the filter chain.
    filterChain.doFilter(request, response);
  }
}
