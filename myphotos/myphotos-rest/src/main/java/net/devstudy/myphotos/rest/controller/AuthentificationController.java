/*
 * Copyright 2017 </>DevStudy.net.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.devstudy.myphotos.rest.controller;

import java.util.Optional;
import java.util.Set;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import javax.validation.groups.Default;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;
import net.devstudy.myphotos.common.converter.ModelConverter;
import net.devstudy.myphotos.common.annotation.qualifier.Facebook;
import net.devstudy.myphotos.common.annotation.qualifier.GooglePlus;
import net.devstudy.myphotos.common.annotation.group.SignUpGroup;
import net.devstudy.myphotos.exception.RetrieveSocialDataFailedException;
import net.devstudy.myphotos.model.domain.AccessToken;
import net.devstudy.myphotos.model.domain.Profile;
import static net.devstudy.myphotos.rest.Constants.ACCESS_TOKEN_HEADER;
import net.devstudy.myphotos.rest.converter.ConstraintViolationConverter;
import net.devstudy.myphotos.rest.model.AuthentificationCodeREST;
import net.devstudy.myphotos.rest.model.ProfileREST;
import net.devstudy.myphotos.rest.model.SignUpProfileREST;
import net.devstudy.myphotos.rest.model.SimpleProfileREST;
import net.devstudy.myphotos.rest.model.ValidationResultREST;
import net.devstudy.myphotos.service.AccessTokenService;
import net.devstudy.myphotos.service.ProfileService;
import net.devstudy.myphotos.service.SocialService;
import org.apache.commons.lang3.StringUtils;

/**
 *
 *
 * @author devstudy
 * @see http://devstudy.net
 */
@Path("/auth")
@Produces(APPLICATION_JSON)
@RequestScoped
public class AuthentificationController {

    @Inject
    @GooglePlus
    private SocialService googplePlusSocialService;

    @Inject
    @Facebook
    private SocialService facebookSocialService;

    @EJB
    private ProfileService profileService;
    
    @EJB
    private AccessTokenService accessTokenService;
    
    @Inject
    private ModelConverter converter;
    
    @Resource(lookup = "java:comp/Validator")
    private Validator validator;
    
    @Inject
    private ConstraintViolationConverter constraintViolationConverter;

    @POST
    @Path("/sign-in/facebook")
    @Consumes(APPLICATION_JSON)
    public Response facebookSignIn(AuthentificationCodeREST authentificationCode) {
        return auth(authentificationCode, facebookSocialService);
    }
    
    @POST
    @Path("/sign-up/facebook")
    @Consumes(APPLICATION_JSON)
    public Response facebookSignUp(SignUpProfileREST signUpProfile) {
        return auth(signUpProfile, facebookSocialService);
    }
    
    @POST
    @Path("/sign-in/google-plus")
    @Consumes(APPLICATION_JSON)
    public Response googplePlusSignIn(AuthentificationCodeREST authentificationCode) {
        return auth(authentificationCode, googplePlusSocialService);
    }
    
    @POST
    @Path("/sign-up/google-plus")
    @Consumes(APPLICATION_JSON)
    public Response googplePlusSignUp(SignUpProfileREST signUpProfile) {
        return auth(signUpProfile, googplePlusSocialService);
    }
    
    @POST
    @Path("/sign-out")
    public Response signOut(
            @HeaderParam(ACCESS_TOKEN_HEADER) String token) {
        accessTokenService.invalidateAccessToken(token);
        return Response.ok().build();
    }
    
    protected Response auth(AuthentificationCodeREST model, SocialService socialService) {
        validateCode(model.getCode());
        Profile profile = socialService.fetchProfile(model.getCode());
        Optional<Profile> profileOptional = profileService.findByEmail(profile.getEmail());
        if(profileOptional.isPresent()) {
            Profile signedInProfile = profileOptional.get();
            AccessToken accessToken = accessTokenService.generateAccessToken(signedInProfile);
            return buidResponse(OK, signedInProfile, Optional.of(accessToken.getToken()), SimpleProfileREST.class);      
        } else if(model instanceof SignUpProfileREST) {
            return processSignUp((SignUpProfileREST)model);
        } else {
            profileService.translitSocialProfile(profile);
            return buidResponse(NOT_FOUND, profile, Optional.empty(), ProfileREST.class);
        }
    }
    
    protected Response buidResponse(Status status, Profile profile, Optional<String> accessToken, Class<?> resultClass) {
        Response.ResponseBuilder builder = Response.status(status);
        builder.entity(converter.convert(profile, resultClass));
        if(accessToken.isPresent()) {
            builder.header(ACCESS_TOKEN_HEADER, accessToken.get());
        }
        return builder.build();
    }
    
    protected void validateCode(String code) {
        if(StringUtils.isBlank(code)) {
            throw new RetrieveSocialDataFailedException("Code is required");
        }
    }

    protected Response processSignUp(SignUpProfileREST signUpProfile) {
        Set<ConstraintViolation<SignUpProfileREST>> violations = validator.validate(signUpProfile, Default.class);
        if(violations.isEmpty()) {
            Profile profile = new Profile();
            signUpProfile.copyToProfile(profile);
            profileService.signUp(profile, true);
            AccessToken accessToken = accessTokenService.generateAccessToken(profile);
            return buidResponse(OK, profile, Optional.of(accessToken.getToken()), SimpleProfileREST.class);
        } else{
            ValidationResultREST validationResult = constraintViolationConverter.convert(violations);
            return Response.status(BAD_REQUEST).entity(validationResult).build();
        }
    }
}

